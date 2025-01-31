#include "common.h"

#include "jvmtiutil.h"
#include "connection.h"
#include "network.h"
#include "msgchannel.h"
#include "dislserver.pb-c.h"

#include "bytecode.h"
#include "classparser.h"
#include "codeflags.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <jni.h>
#include <jvmti.h>


// ****************************************************************************
// AGENT CONFIG
// ****************************************************************************

#define DISL_SERVER_HOST "disl.server.host"
#define DISL_SERVER_HOST_DEFAULT "localhost"

#define DISL_SERVER_PORT "disl.server.port"
#define DISL_SERVER_PORT_DEFAULT "11217"

#define DISL_BYPASS "disl.bypass"
#define DISL_BYPASS_DEFAULT "dynamic"

#define DISL_SPLIT_METHODS "disl.splitmethods"
#define DISL_SPLIT_METHODS_DEFAULT true

#define DISL_CATCH_EXCEPTIONS "disl.excepthandler"
#define DISL_CATCH_EXCEPTIONS_DEFAULT false

#define DISL_FORCE_SUPERCLASS "disl.forcesuperclass"
#define DISL_FORCE_SUPERCLASS_DEFAULT false

#define DISL_FORCE_INTERFACES "disl.forceinterfaces"
#define DISL_FORCE_INTERFACES_DEFAULT false

#define DISL_DEBUG "debug"
#define DISL_DEBUG_DEFAULT false


/**
 * The instrumentation bypass mode.
 * NOTE: The ordering of the modes is important!
 */
enum bypass_mode {
	/**
	 * The original method code will not be preserved. Consequently, the
	 * instrumented version of a method will be always executed, even when
	 * invoked inside a DiSL snippet.
	 */
	BYPASS_MODE_DISABLED = 0,

	/**
	 * The original method code will be preserved, and will be always
	 * executed instead of the instrumented version during JVM bootstrap.
	 * After bootstrap, only the instrumented version of a method will
	 * be executed.
	 */
	BYPASS_MODE_BOOTSTRAP = 1,

	/**
	 * The original method code will be preserved, and executed whenever
	 * invoked from inside a DiSL snippet.
	 */
	BYPASS_MODE_DYNAMIC = 2
};


/**
 * Flags representing code options, derived from the values generated from Java.
 */
enum code_flags {
	CF_CREATE_BYPASS = ch_usi_dag_disl_DiSL_CodeOption_Flag_CREATE_BYPASS,
	CF_DYNAMIC_BYPASS = ch_usi_dag_disl_DiSL_CodeOption_Flag_DYNAMIC_BYPASS,
	CF_SPLIT_METHODS = ch_usi_dag_disl_DiSL_CodeOption_Flag_SPLIT_METHODS,
	CF_CATCH_EXCEPTIONS = ch_usi_dag_disl_DiSL_CodeOption_Flag_CATCH_EXCEPTIONS,
};


struct config {
	char * server_host;
	char * server_port;

	enum bypass_mode bypass_mode;
	bool split_methods;
	bool catch_exceptions;
	bool force_superclass;
	bool force_interfaces;

	bool debug;
};


/**
 * Agent configuration.
 */
static struct config agent_config;


/**
 * Code option flags that control the instrumentation.
 */
static volatile jint agent_code_flags;


/**
 * Flag indicating that the VM has been started, which
 * allows calling any JNI function.
 */
static volatile bool jvm_is_started;


/**
 * Flag indicating that the VM has been initialized,
 * which allows calling any JNI or JVMTI function.
 */
static volatile bool jvm_is_initialized;


/**
 * Flag indicating that the VM is using Java 9 or above,
 * which allows calling JNI module system functions.
 */
static volatile bool java_is_9_or_above;


/**
 * Runtime debuging output macros.
 */
#define rdexec \
	if (agent_config.debug)

#define rdoutput(args...) \
	fprintf (stdout, args);

#define __safe_name(name) \
	(((name) == NULL) ? "<unknown>" : (name))

#define rdaprefix(args...) \
	rdoutput ("disl-agent: "); \
	rdoutput (args);

#define rdatprefix(info, args...) \
	rdoutput ("disl-agent [%s]: ", __safe_name ((info)->name)); \
	rdoutput (args);

#define rdaprintf(args...) \
	rdexec { \
		rdaprefix (args); \
	}

#define rdatprintf(info, args...) \
	rdexec { \
		rdatprefix (info, args); \
	}


// ****************************************************************************
// CLASS FILE LOAD
// ****************************************************************************

static jint
__calc_code_flags (struct config * config, bool jvm_is_booting) {
	jint result = 0;

	//
	// If bypass is desired, always create bypass code while the JVM is
	// bootstrapping. If dynamic bypass is desired, create bypass code as
	// well as code to control it dynamically.
	//
	if (config->bypass_mode > BYPASS_MODE_DISABLED) {
		result |= jvm_is_booting ? CF_CREATE_BYPASS : 0;
		if (config->bypass_mode > BYPASS_MODE_BOOTSTRAP) {
			result |= (CF_CREATE_BYPASS | CF_DYNAMIC_BYPASS);
		}
	}

	result |= config->split_methods ? CF_SPLIT_METHODS : 0;
	result |= config->catch_exceptions ? CF_CATCH_EXCEPTIONS : 0;

	return result;
}


static inline const char *
__safe (const char * class_name) {
	return (class_name != NULL) ? class_name : "<unknown class>";
}


/**
 * Sends the given class to the remote server for instrumentation. If the
 * server modified the class, provided class definition structure is updated
 * and the function returns TRUE. Otherwise, the structure is left unmodified
 * and FALSE is returned.
 *
 * NOTE: The class_name parameter may be NULL -- this is often the case for
 * anonymous classes.
 */
static bool
__instrument_class (
	jint request_flags, const char * class_name,
	jvmtiClassDefinition * class_def
) {
	//
	// Put the class data into a request message, acquire a connection and
	// send the it to the server. Receive the response and release the
	// connection again.
	//
	InstrumentClassRequest request = INSTRUMENT_CLASS_REQUEST__INIT;
	request.flags = request_flags;
	request.classname = (char *) class_name;
	request.classbytes.len = class_def->class_byte_count;
	request.classbytes.data = (uint8_t *) class_def->class_bytes;

	size_t send_size = instrument_class_request__get_packed_size (&request);
	void * send_buffer = malloc (send_size);
	assert (send_buffer != NULL);

	instrument_class_request__pack (&request, send_buffer);
	struct connection * conn = network_acquire_connection ();
	message_send (conn, send_buffer, send_size);

	//

	void * recv_buffer;
	size_t recv_size = message_recv (conn, &recv_buffer);
	network_release_connection (conn);

	InstrumentClassResponse * response = instrument_class_response__unpack (NULL, recv_size, recv_buffer);
	assert (response != NULL);
	free (recv_buffer);

	//
	// Check if error occurred on the server.
	// The control field of the response contains the error message.
	//
	if (response->result == INSTRUMENT_CLASS_RESULT__ERROR) {
		fprintf (
			stderr, "%sinstrumentation server error:\n%s\n",
			ERROR_PREFIX, response->errormessage
		);

		exit (ERROR_SERVER);
	}

	//
	// Update the class definition and signal that the class has been
	// modified if non-empty class code has been returned. Otherwise,
	// signal that the class has not been modified.
	//
	if (response->result == INSTRUMENT_CLASS_RESULT__CLASS_MODIFIED) {
		class_def->class_byte_count = response->classbytes.len;
		class_def->class_bytes = response->classbytes.data;

		response->classbytes.len = 0;
		response->classbytes.data = NULL;

		instrument_class_response__free_unpacked (response, NULL);
		return true;

	} else {
		instrument_class_response__free_unpacked (response, NULL);
		return false;
	}
}


static jclass
__jni_find_class (JNIEnv * jni, const char * class_name) {
	assert (jni != NULL && jvm_is_started);

	jclass found_class = (* jni)->FindClass (jni, class_name);
	if (found_class == NULL) {
		warn ("class %s not found\n", class_name);
		if ((* jni)->ExceptionCheck (jni)) {
			rdexec {
				(* jni)->ExceptionDescribe (jni);
			}

			(* jni)->ExceptionClear (jni);
			rdaprintf ("cleared pending exception\n");
		}
	}

	return found_class;
}


static void
__force_class (JNIEnv * jni, const char * class_name, const char * kind) {
	rdaprintf ("\tforce-loading %s %s\n", kind, class_name);
	jclass found_class = __jni_find_class (jni, class_name);
	if (found_class != NULL) {
		// Clear the local reference.
		(* jni)->DeleteLocalRef (jni, found_class);
	} else {
		warn ("failed to force-load %s %s\n", kind, class_name);
	}
}

static void
__force_superclass (JNIEnv * jni, class_t inst_class) {
	char * super_name = class_super_class_name (inst_class);
	if (super_name != NULL) {
		__force_class (jni, super_name, "super class");
		free (super_name);

	} else {
		rdaprintf ("\tclass does not have super class\n");
	}
}


static void
__force_interfaces (JNIEnv * jni, class_t inst_class) {
	int count = class_interface_count (inst_class);
	if (count == 0) {
		rdaprintf ("\tclass does not implement interfaces\n");
		return;
	}

	for (int index = 0; index < count; index++) {
		char * iface_name = class_interface_name (inst_class, index);
		if  (iface_name != NULL) {
			__force_class (jni, iface_name, "interface");
			free (iface_name);

		} else {
			warn ("failed to get the name of interface %d\n", index);
		}
	}
}


/**
 * Forces loading of the super class (or the implemented interfaces) of the
 * class being instrumented. For this, we need to parse the constant pool
 * of the class to discover the names of the classes to be force-loaded.
 * We then use JNI to find the classes, which will force their loading.
 */
static void
__force_classes (
	JNIEnv * jni, const char * class_name,
	const unsigned char * class_bytes, jint class_byte_count
) {
	assert (jni != NULL && jvm_is_started);

	class_t inst_class = class_alloc (class_bytes, class_byte_count);
	if (inst_class != NULL) {
		if (agent_config.force_superclass) {
			__force_superclass (jni, inst_class);
		}

		if (agent_config.force_interfaces) {
			__force_interfaces (jni, inst_class);
		}

		class_free (inst_class);

	} else {
		warn ("failed to parse class %s\n", __safe (class_name));
	}
}


static char *
__thread_name (jvmtiEnv * jvmti, JNIEnv * jni) {
	assert (jvmti != NULL && jvm_is_initialized);

	jvmtiThreadInfo info;
	jvmtiError error = (* jvmti)->GetThreadInfo (jvmti, NULL /* current */, &info);
	check_jvmti_error (jvmti, error, "failed to get current thread info");

	//
	// Release the thread_group and context_class_loader
	// references because we do not need them.
	//
	if (jni != NULL) {
		(* jni)->DeleteLocalRef (jni, info.thread_group);
		(* jni)->DeleteLocalRef (jni, info.context_class_loader);
	}

	return info.name;
}


struct thread_info {
	char * name;
};

#define INIT_THREAD_INFO { .name = NULL }


static void
__thread_info_init (jvmtiEnv * jvmti, JNIEnv * jni, struct thread_info * info) {
	if (jvmti != NULL && jvm_is_initialized) {
		info->name = __thread_name (jvmti, jni);
	}
}


static void
__thread_info_done (jvmtiEnv * jvmti, struct thread_info * info) {
	if (info->name != NULL) {
		(* jvmti)->Deallocate (jvmti, (unsigned char *) info->name);
		info->name = NULL;
	}
}


static void JNICALL
jvmti_callback_class_file_load (
	jvmtiEnv * jvmti, JNIEnv * jni,
	jclass class_being_redefined, jobject loader,
	const char * class_name, jobject protection_domain,
	jint class_byte_count, const unsigned char * class_bytes,
	jint * new_class_byte_count, unsigned char ** new_class_bytes
) {
	struct thread_info info = INIT_THREAD_INFO;
	rdexec {
		__thread_info_init (jvmti, jni, &info);

		rdatprefix (
			&info, "processing %s (%ld bytes)\n",
			__safe (class_name), (long) class_byte_count
		);
	}

	//
	// Avoid instrumenting the bypass check class.
	//
	if (class_name != NULL && strcmp (class_name, BPC_CLASS_NAME) == 0) {
		rdatprintf (&info, "ignored %s (bypass check class)\n", class_name);
		goto __release_thread_info;
	}

	//
	// Force loading of the super class or the interface classes. This is
	// only used when the VM is out of the primordial phase, which allows
	// JNI calls to be made.
	//
	if (agent_config.force_superclass || agent_config.force_interfaces) {
		if (jni != NULL) {
			rdatprintf (&info, "forcing lookup of dependent classes for %s\n", __safe (class_name));
			__force_classes (jni, class_name, class_bytes, class_byte_count);
		} else {
			rdatprintf (&info, "VM still primordial, skipping lookup of dependent classes for %s\n", __safe (class_name));
		}
	}

	//
	// Instrument the class and if changed by the server, provide the
	// code to the JVM in its own memory.
	//
	jvmtiClassDefinition class_def = {
		.class_byte_count = class_byte_count,
		.class_bytes = class_bytes,
	};

	bool class_changed = __instrument_class (
		agent_code_flags, class_name, &class_def
	);

	if (class_changed) {
		//
		// Enable module to read bypass check class.
		//
		if (java_is_9_or_above && jvm_is_initialized && strstr(class_name, "/") != NULL) {
			char* class_wt_package = strrchr (class_name, '/');
			int package_name_length = strlen(class_name) - strlen(class_wt_package);
			char package_name[package_name_length + 1];
			strncpy (package_name, class_name, package_name_length);
			package_name[package_name_length] = '\0';

			jvmtiError error;
			jobject bypass_module = NULL;
			error = (*jvmti)->GetNamedModule (jvmti, NULL, "ch/usi/dag/disl/dynamicbypass", &bypass_module);
			check_jvmti_error (jvmti, error, "failed to get bypass module");

			jobject class_module = NULL;
			error = (*jvmti)->GetNamedModule (jvmti, loader, package_name, &class_module);
			check_jvmti_error (jvmti, error, "failed to get logger module");

			if (class_module != NULL) {
				error = (*jvmti)->AddModuleExports (jvmti, bypass_module, "ch.usi.dag.disl.dynamicbypass", class_module);
				check_jvmti_error (jvmti, error, "failed to export bypass package to module");
			}
		}

		unsigned char * jvm_class_bytes = jvmti_alloc_copy (
			jvmti, class_def.class_bytes, class_def.class_byte_count
		);

		free ((void *) class_def.class_bytes);

		*new_class_byte_count = class_def.class_byte_count;
		*new_class_bytes = jvm_class_bytes;

		rdatprintf (
			&info, "redefined %s (%ld bytes)\n", 
			__safe (class_name), (long) class_def.class_byte_count
		);
	} else {
		rdatprintf (&info, "loaded %s (unmodified)\n", __safe (class_name));
	}

__release_thread_info:
	rdexec {
		__thread_info_done (jvmti, &info);
	}
}


// ****************************************************************************
// JVMTI EVENT: VM INIT
// ****************************************************************************

static void JNICALL
jvmti_callback_vm_init (jvmtiEnv * jvmti, JNIEnv * jni, jthread thread) {
	//
	// Update flags to reflect that the VM has stopped booting.
	//
	jvm_is_initialized = true;
	agent_code_flags = __calc_code_flags (&agent_config, false);

	struct thread_info info = INIT_THREAD_INFO;
	rdexec {
		__thread_info_init (jvmti, jni, &info);
		rdatprefix (&info, "vm_init (the VM has been initialized)\n");
	}

	//
	// Redefine the bypass check class. If dynamic bypass is required, use
	// a class that honors the dynamic bypass state for the current thread.
	// Otherwise use a class that disables bypassing instrumented code.
	//
	jvmtiClassDefinition * bpc_classdef;
	if (agent_config.bypass_mode == BYPASS_MODE_DYNAMIC) {
		rdatprintf (&info, "redefining BypassCheck for dynamic bypass\n");
		bpc_classdef = &dynamic_BypassCheck_classdef;
	} else {
		rdatprintf (&info, "redefining BypassCheck to disable bypass\n");
		bpc_classdef = &never_BypassCheck_classdef;
	}

	if (!jvmti_redefine_class (jvmti, jni, BPC_CLASS_NAME, bpc_classdef)) {
		warn ("could not find the %s class\n", BPC_CLASS_NAME);
	}

	rdexec {
		rdatprefix (&info, "vm_init complete\n");
		__thread_info_done (jvmti, &info);
	}
}


// ****************************************************************************
// JVMTI EVENT: VM START
// ****************************************************************************

static void JNICALL
jvmti_callback_vm_start (jvmtiEnv * jvmti, JNIEnv * jni) {
	rdaprintf ("vm_start (the VM has been started)\n");

	//
	// Update flags to reflect that the VM has started, and that any
	// JNI function can be called from now on (but not before the
	// handler returns).
	//
	jvm_is_started = true;
}


// ****************************************************************************
// JVMTI EVENT: VM DEATH
// ****************************************************************************

static void JNICALL
jvmti_callback_vm_death (jvmtiEnv * jvmti, JNIEnv * jni) {
	rdexec {
		struct thread_info info = INIT_THREAD_INFO;
		__thread_info_init (jvmti, jni, &info);
		rdatprefix (&info, "vm_death (the VM is shutting down)\n");
		__thread_info_done (jvmti, &info);
	}

	//
	// Update flags to reflect that the VM is shutting down.
	//
	jvm_is_initialized = false;
	jvm_is_started = false;
}


// ****************************************************************************
// AGENT ENTRY POINT: ON LOAD
// ****************************************************************************

static void
__configure_from_properties (jvmtiEnv * jvmti, struct config * config) {
	//
	// Get bypass mode configuration
	//
	char * bypass = jvmti_get_system_property_string (
		jvmti, DISL_BYPASS, DISL_BYPASS_DEFAULT
	);

	static const char * values [] = { "disabled", "bootstrap", "dynamic" };
	int bypass_index = find_value_index (bypass, values, sizeof_array (values));
	check_error (bypass_index < 0, "invalid bypass mode, check " DISL_BYPASS);

	config->bypass_mode = bypass_index;
	free (bypass);

	//
	// Get boolean values from system properties
	//
	config->split_methods = jvmti_get_system_property_bool (
		jvmti, DISL_SPLIT_METHODS, DISL_SPLIT_METHODS_DEFAULT
	);

	config->catch_exceptions = jvmti_get_system_property_bool (
		jvmti, DISL_CATCH_EXCEPTIONS, DISL_CATCH_EXCEPTIONS_DEFAULT
	);

	config->force_superclass = jvmti_get_system_property_bool (
		jvmti, DISL_FORCE_SUPERCLASS, DISL_FORCE_SUPERCLASS_DEFAULT
	);

	config->force_interfaces = jvmti_get_system_property_bool (
		jvmti, DISL_FORCE_INTERFACES, DISL_FORCE_INTERFACES_DEFAULT
	);

	config->debug = jvmti_get_system_property_bool (
		jvmti, DISL_DEBUG, DISL_DEBUG_DEFAULT
	);

	//
	// Configuration summary. Current thread does not exist yet.
	//
	rdexec {
		rdaprefix ("bypass mode: %s\n", values [bypass_index]);
		rdaprefix ("split methods: %d\n", config->split_methods);
		rdaprefix ("catch exceptions: %d\n", config->catch_exceptions);
		rdaprefix ("force superclass: %d\n", config->force_superclass);
		rdaprefix ("force interfaces: %d\n", config->force_interfaces);
		rdaprefix ("debug: %d\n", config->debug);
	}
}


static void
__configure_from_options (const char * options, struct config * config) {
	//
	// Assign default host name and port and bail out
	// if there are no agent options.
	//
	if (options == NULL) {
		config->server_host = strdup (DISL_SERVER_HOST_DEFAULT);
		config->server_port = strdup (DISL_SERVER_PORT_DEFAULT);
		return;
	}

	//
	// Parse the host name and port of the remote server.
	// Look for port specification first, then take the prefix
	// before ':' as the host name.
	//
	char * host_start = strdup (options);
	char * port_start = strchr (host_start, ':');
	if (port_start != NULL) {
		//
		// Split the option string at the port delimiter (':')
		// using an end-of-string character ('\0') and copy
		// the port.
		//
		port_start [0] = '\0';
		port_start++;

		config->server_port = strdup (port_start);
	}

	config->server_host = strdup (host_start);
}


static jvmtiEnv *
__get_jvmti (JavaVM * jvm) {
	jvmtiEnv * jvmti = NULL;

	jint result = (*jvm)->GetEnv (jvm, (void **) &jvmti, JVMTI_VERSION_1_0);
	if (result != JNI_OK || jvmti == NULL) {
		//
		// The VM was unable to provide the requested version of the
		// JVMTI interface. This is a fatal error for the agent.
		//
		fprintf (
			stderr,
			"%sFailed to obtain JVMTI interface Version 1 (0x%x)\n"
			"JVM GetEnv() returned %ld - is your Java runtime "
			"version 1.5 or newer?\n",
			ERROR_PREFIX, JVMTI_VERSION_1, (long) result
		);

		exit (ERROR_JVMTI);
	}

	return jvmti;
}


JNIEXPORT jint JNICALL VISIBLE
Agent_OnLoad (JavaVM * jvm, char * options, void * reserved) {
	jvmtiEnv * jvmti = __get_jvmti (jvm);

	// add capabilities
	jvmtiCapabilities caps = {
		.can_redefine_classes = 1,
		.can_generate_all_class_hook_events = 1,
	};

	//
	// Add class events in primordial phase only if available
	// i.e. in Java9+
	//
	jvmtiError error;
	jvmtiCapabilities potentialCapabilities;
	error = (*jvmti)->GetPotentialCapabilities(jvmti, &potentialCapabilities);
	check_jvmti_error(jvmti, error,
			"Unable to get potential JVMTI capabilities.");

	if(potentialCapabilities.can_generate_early_class_hook_events) {
		caps.can_generate_early_class_hook_events = 1;
	}

	error = (*jvmti)->AddCapabilities (jvmti, &caps);
	check_jvmti_error (jvmti, error, "failed to add capabilities");


	// configure agent and init connections
	__configure_from_options (options, &agent_config);
	__configure_from_properties (jvmti, &agent_config);

	char * java_version;
	error = (*jvmti)->GetSystemProperty (jvmti, "java.vm.specification.version", &java_version);
	check_jvmti_error (jvmti, error, "failed to get java version");

	// Java specification version contains a dot until version 9
	// eg. Java 8 = 1.8
	//	   Java 9 = 9
	java_is_9_or_above = strstr(java_version, ".") == NULL;
	(* jvmti)->Deallocate (jvmti, (unsigned char *) java_version);

	jvm_is_started = false;
	jvm_is_initialized = false;
	agent_code_flags = __calc_code_flags (&agent_config, true);

	rdaprintf ("agent loaded, initializing connections\n");
	network_init (agent_config.server_host, agent_config.server_port);


	// register callbacks
	jvmtiEventCallbacks callbacks = {
		.VMStart = &jvmti_callback_vm_start,
		.VMInit = &jvmti_callback_vm_init,
		.VMDeath = &jvmti_callback_vm_death,
		.ClassFileLoadHook = &jvmti_callback_class_file_load,
	};

	error = (*jvmti)->SetEventCallbacks (jvmti, &callbacks, (jint) sizeof (callbacks));
	check_jvmti_error (jvmti, error, "failed to register event callbacks");


	// enable event notification
	error = (*jvmti)->SetEventNotificationMode (jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_START, NULL);
	check_jvmti_error (jvmti, error, "failed to enable VM START event");

	error = (*jvmti)->SetEventNotificationMode (jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
	check_jvmti_error (jvmti, error, "failed to enable VM INIT event");

	error = (*jvmti)->SetEventNotificationMode (jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, NULL);
	check_jvmti_error (jvmti, error, "failed to enable VM DEATH event");

	error = (*jvmti)->SetEventNotificationMode (jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
	check_jvmti_error (jvmti, error, "failed to enable CLASS FILE LOAD event");

	return 0;
}


// ****************************************************************************
// AGENT ENTRY POINT: ON UNLOAD
// ****************************************************************************

JNIEXPORT void JNICALL VISIBLE
Agent_OnUnload (JavaVM * jvm) {
	rdaprintf ("agent unloaded, closing connections\n");

	//
	// Just close all the connections.
	//
	network_fini ();
}
