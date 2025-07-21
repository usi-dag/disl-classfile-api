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
#include <time.h>


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

#define DISL_FL_EXCLUSION_FILE "disl.flexclusionfile"
#define DISL_FL_EXCLUSION_FILE_DEFAULT ""

#define DISL_PRINT_CLASS_LOADED "disl.printclassloaded"
#define DISL_PRINT_CLASS_LOADED_DEFAULT false

#define DISL_DEBUG "debug"
#define DISL_DEBUG_DEFAULT false

//Force-loading macros
#define DISL_CLASSLOADER_INITIAL_TAG 10000
#define DISL_LOADER_DISABLED -2
#define DISL_LOADER_UNKNOWN -1
//#define DISL_DEBUG_PRINT_TC_LIST 1 //Uncomment (or define this macro through command line) for verbose printing of type_and_classloader lists

// message types must match the ones defined in DiSLServer.java
// implicit message type 0 = shutdown signal
#define DISL_MESSAGE_INSTRUMENT 1
#define DISL_MESSAGE_CONTROLLER 2

static int nLoadedClasses = 0;


static jint classloader_tag = DISL_CLASSLOADER_INITIAL_TAG;
// static jclass fl_klass = NULL;
static jmethodID fl_klassGetClassLoaderMethod = NULL;

struct list_of_strings {
	char * str;
	struct list_of_strings * next;
};

static struct list_of_strings * fl_exclusion_list = NULL;


static void __free_list_of_strings(struct list_of_strings * root) {
	if (root == NULL) {
		return;
	}

	__free_list_of_strings(root->next);
	free(root->str);
	free(root);
}


// Global JVMTI environment (needed by the Deploy API)
static jvmtiEnv * jvmti;


/* This functions are intended to support critical sections
 * during object tagging. At the moment, they are not utilized.
 * They are kept here for future needs.
 */

static jrawMonitorID fl_lock;

static void __check_jvmti_error(jvmtiEnv *jvmti, jvmtiError errnum,
		const char *str) {
	if (errnum != JVMTI_ERROR_NONE) {
		char *errnum_str;

		errnum_str = NULL;
		(* jvmti)->GetErrorName(jvmti, errnum, &errnum_str);

		jvmtiPhase ph;
		(* jvmti)->GetPhase(jvmti, &ph);

		printf("ERROR: JVMTI: %d(%s): %s (phase: %d)\n", errnum,
				(errnum_str == NULL ? "Unknown" : errnum_str),
				(str == NULL ? "" : str), ph);


	}
}

static void __createForceLoadingMonitor(jvmtiEnv * jvmti) {

	jvmtiError error = (*jvmti)->CreateRawMonitor(jvmti, "force loading monitor", &fl_lock);
	__check_jvmti_error(jvmti, error, "Cannot create force-loading monitor");

}

static void __enterForceLoadingMonitor(jvmtiEnv * jvmti) {

	if (fl_lock == NULL) {
		__createForceLoadingMonitor(jvmti);
	}

	jvmtiError error = (*jvmti)->RawMonitorEnter(jvmti, fl_lock);
	__check_jvmti_error(jvmti, error, "Cannot enter force-loading monitor");

}

static void __exitForceLoadingMonitor(jvmtiEnv * jvmti) {

	assert(fl_lock != NULL);

	jvmtiError error = (*jvmti)->RawMonitorExit(jvmti, fl_lock);
	__check_jvmti_error(jvmti, error, "Cannot exit force-loading monitor");

}

static void __waitForceLoadingMonitor(jvmtiEnv * jvmti) {

	assert(fl_lock != NULL);

	jvmtiError error = (*jvmti)->RawMonitorWait(jvmti, fl_lock, 0);
	__check_jvmti_error(jvmti, error, "Cannot wait on force-loading monitor");


}


static void __notifyForceLoadingMonitor(jvmtiEnv * jvmti) {

	assert(fl_lock != NULL);

	jvmtiError error = (*jvmti)->RawMonitorNotify(jvmti, fl_lock);
	__check_jvmti_error(jvmti, error, "Cannot notify on force-loading monitor");


}

//

static bool __isVMinPhase(jvmtiEnv *jvmti, jvmtiPhase phase) {

	jvmtiPhase ph;
	(* jvmti)->GetPhase(jvmti, &ph);
	return ph == phase;

}

static bool __isLivePhase(jvmtiEnv *jvmti) {

	return __isVMinPhase(jvmti, JVMTI_PHASE_LIVE);

}

static bool __isStartPhase(jvmtiEnv *jvmti) {

	return __isVMinPhase(jvmti, JVMTI_PHASE_START);
}



/*
 * This struct stores information on the supertypes of a class.
 * In particular, it store the name of the supertype and the tag of
 * the classloader used to load the supertype.
 *
 * The struct is intended to be used as a node into a singly linked list.
 * Each node should store information of a single supertype.
 * If more supertypes are present, more instances of this struct should be
 * concatenated. The struct of the last supertype should have next == NULL.
 */
struct type_and_classloader {
	char * typename;
	jint classloader;
	struct type_and_classloader * next;
};

/*
 * Helper struct used to convert information in type_and_classloader
 * into a format suitable for network communication.
 *
 * typename_arr is a pointer to a string (here expressed as unsigned bytes) storing
 * the names of all the supertypes of a given class. Names of different supertypes are concatenated
 * and separated by the '!' character. This string must be long typename_length byte, and must terminate
 * with a '!'. For example, "java/lang/Object!java/lang/Runnable!" is a valid string.
 *
 * classloader_arr is a pointer to an array of bytes, encoding classloaders tags.
 * classloader_arr must be long classloader_length byte, where classloader_length must be a multiple
 * of sizeof(jint) (or 0). Each group of 4 byte [i.e., the current sizeof(jint)] represent a
 * complete tag. For example, the sequence of bytes 0x0 0x0 0x27 0x10 denotes the jint 10000
 * (which is also a classloader tag). The sequence  0x0 0x0 0x0 0x0 0x0 0x0 0x27 0x10 denotes
 * two jint, 0 (0x0 0x0 0x0 0x0) and 10000 (0x0 0x0 0x27 0x10).
 *
 * Each classloader tag in classloader_arr must match the corresponding string in typename_arr.
 * For example, the following values:
 * 		classloader_arr = "java/lang/Object!my/test/package/Main!"
 * 		typename_arr = [0x0 0x0 0x0 0x0 0x0 0x0 0x27 0x10]
 * encode the following supertypes:
 * <java/lang/Object, loaded by classloader 0 (the bootstrap class loader)>
 * <my/test/package/Main, loaded by classloader 10000>
 *
 */

struct tc_array {
	jint typename_length;
	jint classloader_length;
	uint8_t * typename_arr;
	uint8_t * classloader_arr;
};

/* Helper struct, used to know how much space to allocate in tc_array
 *
 */

struct tc_length {
	jint n_struct; 				//# of nodes in a list of type_and_classloader elements
	jint total_tag_size; 		//total # of bytes composing the classloader tag stream (i.e., n_struct * 4)
	jint total_string_size; 		//Total size of each typename string. This size INCLUDES the terminating character '\0'.
};


static void __free_type_and_classloader_list(struct type_and_classloader *root) {

	if (root == NULL) {
		return;
	}

	__free_type_and_classloader_list(root->next);
	free(root->typename);
	free(root);

}


static void __get_type_and_classloader_length(struct type_and_classloader * root, struct tc_length * len) {
	
	len->n_struct = 0;
	len->total_string_size = 0;
	len->total_tag_size = 0;

	if (root == NULL) {
		return;
	}

	struct type_and_classloader * ptr = root;
	while (ptr != NULL) {
		len->n_struct++;
		len->total_tag_size+= sizeof(jint);
		len->total_string_size += strlen(ptr->typename) + 1; //Plus 1 for the '\0'
		ptr = ptr->next;
	}

}


// Converts a jint into an array of bytes
static void __jint_to_byte_array(jint n, uint8_t * bytes) {

	assert(sizeof(jint) == 4);
	*bytes = (n >> 24) & 0xFF;
	*(bytes+1) = (n >> 16) & 0xFF;
	*(bytes+2) = (n >> 8) & 0xFF;
	*(bytes+3) = n & 0xFF;

}

// Constructs a tc_array from a type_and_classloader.
// The tc_array contains a copy of type_and_classloader which is
// suitable for being sent over the network.
static void __build_tc_array(struct type_and_classloader * root, struct tc_array * rootTCA) {

	if (root == NULL) {
		//root can be null if force-loading is disabled,
		//or force-loading of ALL superclass and interfaces failed.
		rootTCA->classloader_arr = NULL;
		rootTCA->typename_arr = NULL;
		rootTCA->classloader_length = 0;
		rootTCA->typename_length = 0;
		return;
	}

	struct type_and_classloader * ptr = root;
	struct tc_length  len;

	__get_type_and_classloader_length(root, &len);

	rootTCA->typename_length = len.total_string_size;
	rootTCA->classloader_length = len.total_tag_size;
	rootTCA->typename_arr = malloc(len.total_string_size);
	rootTCA->classloader_arr = malloc(len.total_tag_size);
	
	uint8_t * typename_temp = rootTCA->typename_arr;
	uint8_t * classloader_temp = rootTCA->classloader_arr;

	while (ptr != NULL) {
		int size = strlen(ptr->typename);
		strcpy((char *) typename_temp, ptr->typename);

		//Replace last character ('\0' to '!')
		typename_temp += size * sizeof(char);
		assert(*typename_temp == '\0');
		*typename_temp = '!';
		typename_temp += sizeof(char);

		uint8_t tmp;
		__jint_to_byte_array(ptr->classloader, &tmp);
		memcpy(classloader_temp, &tmp, sizeof(jint));

		classloader_temp+= sizeof(jint);

		ptr = ptr->next;

	}

}



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
	bool print_loaded_classes;

	char * fl_exclusion_file;

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

#define rdatiprefix(info, args...) \
		rdoutput ("disl-agent [%s, %" PRId64 "]: ", __safe_name ((info)->name), (info)->id); \
		rdoutput (args);

#define rdaprintf(args...) \
		rdexec { \
	rdaprefix (args); \
}

#define rdatprintf(info, args...) \
		rdexec { \
	rdatprefix (info, args); \
}

#define rdatiprintf(info, args...) \
		rdexec { \
	rdatiprefix (info, args); \
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


static jint __set_or_get_classloader_tag(jvmtiEnv * jvmti, jobject loader) {

	assert(__isLivePhase(jvmti) || __isStartPhase(jvmti));

	//If this is the bootstrap class-loader, avoid tagging.
	if (loader == NULL) {
		return 0;
	}

	__enterForceLoadingMonitor(jvmti);

	jlong tag;
	(* jvmti)->GetTag(jvmti, loader, &tag);

	//If no tag set to this classloader yet, tag it
	if (tag == 0) {
		(* jvmti)->SetTag(jvmti, loader, tag = (jlong) classloader_tag++);
	}

	__exitForceLoadingMonitor(jvmti);

	return (jint) tag;

}

//Allocates a new type_and_classloader struct
static struct type_and_classloader *
__allocate_tc_struct() {
	struct type_and_classloader * pointer = malloc(sizeof *pointer);
	pointer->typename = NULL;
	pointer->classloader = DISL_LOADER_UNKNOWN; //Initialize classloader tag to "unknown" number
	pointer->next = NULL;
	return pointer;
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
		jvmtiClassDefinition * class_def, jint loader_tag,
		struct type_and_classloader * tc_list_root
) {


	struct tc_array tcarr;
	__build_tc_array(tc_list_root, &tcarr);

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
	request.classloadertag = loader_tag;
	request.classloaderbytes.len = tcarr.classloader_length;
	request.classloaderbytes.data = tcarr.classloader_arr;
	request.supertypesbytes.len = tcarr.typename_length;
	request.supertypesbytes.data = tcarr.typename_arr;

	size_t send_size = instrument_class_request__get_packed_size (&request);
	void * send_buffer = malloc (send_size);
	assert (send_buffer != NULL);

	instrument_class_request__pack (&request, send_buffer);
	struct connection * conn = network_acquire_connection ();
	message_send (conn, DISL_MESSAGE_INSTRUMENT, send_buffer, send_size);

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


static void
__handle_exception (JNIEnv * jni, jthrowable ex_obj) {
	jclass ex_class = (* jni)->GetObjectClass (jni, ex_obj);
	jmethodID m_getClass = (* jni)->GetMethodID (jni, ex_class, "getClass", "()Ljava/lang/Class;");
	jobject cl_obj = (* jni)->CallObjectMethod (jni, ex_obj, m_getClass);

	jclass cl_class = (* jni)->GetObjectClass (jni, cl_obj);
	jmethodID m_getName = (* jni)->GetMethodID (jni, cl_class, "getName", "()Ljava/lang/String;");
	jstring cl_name = (* jni)->CallObjectMethod (jni, cl_obj, m_getName);

	const char * cl_name_chars = (* jni)->GetStringUTFChars (jni, cl_name, NULL);
	rdaprintf ("\texception %s occured, cleared\n", cl_name_chars);
	(* jni)->ReleaseStringUTFChars (jni, cl_name, cl_name_chars);
}


static jclass __load_class_bootstrap_cl(JNIEnv * jni, const char * class_name) {
	rdaprintf ("loading %s with bootstrap class loader.\n", class_name);
	return (* jni)->FindClass (jni, class_name);
}


static void __do_replace_char(char * str, const char find, const char replace) {
	char *current_pos = strchr(str,find);
	while (current_pos != NULL){
		*current_pos = replace;
		current_pos = strchr(current_pos,find);
	}
}


static char * __replace_char(const char* str, const char find, const char replace){
	char *new_name = strdup(str);
	__do_replace_char(new_name, find, replace);
	return new_name;
}


static char * __replace_slash_with_dot(const char * str) {
	return __replace_char(str, '/', '.');
}


static jclass __load_class(JNIEnv * jni, jobject loader, jint loader_tag, const char * class_name) {

	if (loader == NULL) {
		return __load_class_bootstrap_cl(jni, class_name);
	}
	rdaprintf ("loading %s with user-defined class loader (tag: %d).\n", class_name, loader_tag);
	jclass loaderClass = (*jni)->GetObjectClass(jni, loader);
	if (loaderClass == NULL) {
		warn ("class loader (tag: %d) for %s could not be constructed!!.\n", loader_tag, class_name);
		return NULL;
	}

	jmethodID methodID = (*jni)->GetMethodID(jni, loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
	if (methodID == NULL) {
		warn ("method loadClass() of classloader %d of class %s could not be found.\n", loader_tag, class_name);
		return NULL;
	}

	char * class_name_new = __replace_slash_with_dot(class_name);

	jstring className = (*jni)->NewStringUTF(jni, class_name_new);

	jclass result = (*jni)->CallObjectMethod(jni, loader, methodID, className);

	(*jni)->DeleteLocalRef(jni, className);

	if (result == NULL) {
		jthrowable exception = (* jni)->ExceptionOccurred (jni);
		if (exception != NULL) {
			warn ("Class %s not found by classloader %d - Exception thrown.\n", class_name_new, loader_tag);
			(* jni)->ExceptionClear (jni);
		} else {
			warn ("Class %s not found by classloader %d.\n", class_name_new, loader_tag);
		}
	}

	free(class_name_new);

	return result;
}


static jmethodID __retrieveGetClassLoaderMethod(JNIEnv * jni) {

	if (fl_klassGetClassLoaderMethod != NULL) {
		return fl_klassGetClassLoaderMethod;
	}

	jclass kl = (* jni)->FindClass (jni, "java/lang/Class");
	assert(kl != NULL);
	// fl_klass = (jclass) (* jni) -> NewGlobalRef(jni, kl);
	fl_klassGetClassLoaderMethod = (*jni)->GetMethodID(jni, kl, "getClassLoader", "()Ljava/lang/ClassLoader;");
	assert(fl_klassGetClassLoaderMethod != NULL);

	return fl_klassGetClassLoaderMethod;
}

// Register supertype information in tc_list_root. Returns the address of the next pointer
// to be used by a successive invocation of this method on the same list.

static struct type_and_classloader *
__set_supertype_classloader_info(jvmtiEnv * jvmti, JNIEnv * jni, char * supertype_name,
		jclass supertype, const char * base_class_name) {

	assert(supertype != NULL);

	struct type_and_classloader * tc_list_root = __allocate_tc_struct();

	tc_list_root->typename = supertype_name;

	//If the class is in the java package, we know that its classloader must be 0

	//Important note (June 6, 2017): the below check is not only a "speed-up" of
	// the algorithm. It is actually necessary to not interfere with class initialization.
	// If methods of java.lang.Class (such as getClassLoader()) are called during the
	// START phase, causes some interferences with the initialization
	// of java.lang.Class. This simple check avoids calling such method, and prevents such interference.
	// DO NOT REMOVE!!

	if (strncmp(supertype_name, "java/",5)==0) {
		tc_list_root->classloader = 0;
		return tc_list_root;
	}

	//

	jmethodID methodID = __retrieveGetClassLoaderMethod(jni);

	//Equivalent to: classloader = supertype.getClassLoader();
	jobject classloader = (*jni)->CallObjectMethod(jni, supertype, methodID);

	jthrowable exception = (* jni)->ExceptionOccurred (jni);
	if (exception != NULL) {
		warn("FL:: could not retrieve classloader of %s (baseclass: %s)\n", supertype_name, base_class_name);
		(* jni)->ExceptionDescribe(jni);
		(* jni)->ExceptionClear (jni);
		tc_list_root->classloader = DISL_LOADER_UNKNOWN;
		return tc_list_root;
	}

	//Note: if exception != NULL and classloader == NULL, it means that the class was loaded by the bootstrap classloader.
	//This is not an error

	tc_list_root->classloader =  __set_or_get_classloader_tag(jvmti, classloader);

	return tc_list_root;


}

static struct type_and_classloader *
__force_class (jvmtiEnv * jvmti, JNIEnv * jni, char * class_name, const char * kind, const jobject loader,
		jint loader_tag, const char * base_class_name ){

	assert(kind != NULL && class_name != NULL);
	rdaprintf ("\tforce-loading %s %s - with classloader %d\n", kind, class_name, loader_tag);

	struct type_and_classloader * next_ptr = NULL;
	jclass found_class = __load_class(jni, loader, loader_tag, class_name);

	if (found_class == NULL) {
		warn ("failed to force-load %s %s (triggered by subclass %s) with classloader %d. \n", kind, class_name, base_class_name, loader_tag);
		jthrowable exception = (* jni)->ExceptionOccurred (jni);
		if (exception != NULL) {
			(* jni)->ExceptionClear (jni);
			__handle_exception (jni, exception);
		}
	} else {
		rdaprintf ("force-load for %s %s (triggered by subclass %s) with classloader %d successful.\n", kind, class_name, base_class_name, loader_tag);
		next_ptr = __set_supertype_classloader_info(jvmti, jni, class_name, found_class, base_class_name);
	}

	return next_ptr;

}

static struct type_and_classloader *
__force_superclass (jvmtiEnv * jvmti, JNIEnv * jni, class_t inst_class, const jobject loader,
		jint loader_tag, const char * class_name) {

	char * super_name = class_super_class_name (inst_class);
	struct type_and_classloader * next_ptr = NULL;
	if (super_name != NULL) {
		next_ptr = __force_class (jvmti, jni, super_name, "super class", loader, loader_tag, class_name);

	} else {
		//It should only happen with java.lang.Object
		// (which is impossible to force-load anyways, because it is loaded during the primordial phase).
		rdaprintf ("\tclass does not have super class\n");
	}

	return next_ptr;
}

static void __print_fl_exclusion_list() {

	if (fl_exclusion_list == NULL) {
		rdaprintf ("force-loading exclusion list not defined or empty.\n");
		return;
	}

	struct list_of_strings * ptr = fl_exclusion_list;

	rdaprintf ("start printing content of force-loading exclusion list.\n");
	while(ptr != NULL) {
		rdaprintf (" %s\n",ptr->str);
		ptr = ptr->next;
	}

	rdaprintf ("end printing content of force-loading exclusion list.\n");

}

static struct type_and_classloader *
__force_interfaces (jvmtiEnv * jvmti, JNIEnv * jni, class_t inst_class, const jobject loader,
		jint loader_tag, struct type_and_classloader * tc_list_root, const char * class_name ){
	int count = class_interface_count (inst_class);
	if (count == 0) {
		rdaprintf ("\tclass does not implement interfaces\n");
		return tc_list_root;
	}

	//tc_list_root can be NULL if superclass loading failed.
	//In this case, we need to update tc_list_root at the first
	//interface force-loaded

	struct type_and_classloader * old_ptr = tc_list_root;
	struct type_and_classloader * cur_ptr = NULL;
	for (int index = 0; index < count; index++) {
		char * iface_name = class_interface_name (inst_class, index);
		if  (iface_name != NULL) {
			cur_ptr = __force_class (jvmti, jni, iface_name, "interface", loader, loader_tag, class_name);
			if (old_ptr != NULL) { //equivalent to tc_list_root == NULL
				old_ptr->next = cur_ptr;
				old_ptr=old_ptr->next;
			} else {
				old_ptr = cur_ptr;
				tc_list_root = old_ptr;
			}
		} else {
			warn ("failed to get the name of interface %d\n", index);
		}
	}
	return tc_list_root;

}


/**
 * Forces loading of the super class (or the implemented interfaces) of the
 * class being instrumented. For this, we need to parse the constant pool
 * of the class to discover the names of the classes to be force-loaded.
 * We then use JNI to find the classes, which will force their loading.
 */

static struct type_and_classloader *
__force_classes (
		jvmtiEnv * jvmti, JNIEnv * jni, const char * class_name,
		const unsigned char * class_bytes, jint class_byte_count, const jobject loader,
		jint loader_tag
) {
	assert (jni != NULL && jvm_is_started);

	struct type_and_classloader * tc_list_root = NULL;

	class_t inst_class = class_alloc (class_bytes, class_byte_count);

	if (inst_class != NULL) {
		if (agent_config.force_superclass) {
			tc_list_root = __force_superclass (jvmti, jni, inst_class, loader, loader_tag, class_name);
		}

		if (agent_config.force_interfaces) {
			tc_list_root = __force_interfaces (jvmti, jni, inst_class, loader, loader_tag, tc_list_root, class_name);
		}

		class_free (inst_class);

	} else {
		warn ("failed to parse class %s\n", __safe (class_name));
	}

	return tc_list_root;

}

static jlong
__thread_id (JNIEnv * jni) {
	assert (jni != NULL && jvm_is_started);

	static jclass thread_class;
	if (thread_class == NULL) {
		jclass thread_class_local = (* jni)->FindClass (jni, "java/lang/Thread");
		if (thread_class_local == NULL) {
			return -1;
		}

		thread_class = (jclass) (* jni)->NewGlobalRef (jni, thread_class_local);
		if (thread_class == NULL) {
			return -1;
		}
	}

	//

	static jmethodID m_currentThread;
	if (m_currentThread == NULL) {
		m_currentThread = (* jni)->GetStaticMethodID (jni, thread_class, "currentThread", "()Ljava/lang/Thread;");
		if (m_currentThread == NULL) {
			return -1;
		}
	}

	jobject thread = (* jni)->CallStaticObjectMethod (jni, thread_class, m_currentThread);

	//

	static jmethodID m_getId;
	if (m_getId == NULL) {
		m_getId = (* jni)->GetMethodID (jni, thread_class, "getId", "()J");
		if (m_getId == NULL) {
			return -1;
		}
	}

	return (thread != NULL) ? (* jni)->CallLongMethod (jni, thread, m_getId) : -1;
}


static char *
__thread_name (jvmtiEnv * jvmti, JNIEnv * jni) {
	assert (jvmti != NULL && jvm_is_initialized);

	jvmtiThreadInfo info;
	jvmtiError error = (* jvmti)->GetThreadInfo (jvmti, NULL, &info);
	check_jvmti_error (jvmti, error, "failed to get current thread info");

	//
	// Release the thread_group and context_class_loader
	// references because we dont not need them.
	//
	if (jni != NULL) {
		(* jni)->DeleteLocalRef (jni, info.thread_group);
		(* jni)->DeleteLocalRef (jni, info.context_class_loader);
	}

	return info.name;
}


struct thread_info {
	jlong id;
	char * name;
};

#define INIT_THREAD_INFO { .id = -1, .name = NULL }


static void
__thread_info_init (jvmtiEnv * jvmti, JNIEnv * jni, struct thread_info * info) {
	if (jvmti != NULL && jvm_is_initialized) {
		info->name = __thread_name (jvmti, jni);
	}

	if (jni != NULL && jvm_is_started) {
		info->id = __thread_id (jni);
	}
}


static void
__thread_info_done (jvmtiEnv * jvmti, struct thread_info * info) {
	if (info->name != NULL) {
		(* jvmti)->Deallocate (jvmti, (unsigned char *) info->name);
		info->name = NULL;
	}
}


/* This method is intended to be used for
 * verbose debugging only!
 */

static void __print_tc_list(struct type_and_classloader * root, const char * typename) {

#ifdef DISL_DEBUG_PRINT_TC_LIST
	struct type_and_classloader * ptr = root;

	rdaprintf("Start printing tc_list for %s\n", typename);
	int i = 0;
	while (ptr != NULL) {
		rdaprintf("tc_list of %s - entry %d: Type: %s - Classloader: %d\n", typename, i++, ptr->typename, ptr->classloader);
		ptr = ptr->next;
	}

	rdaprintf("End printing tc_list for %s\n", typename);
#else
	return;
#endif

}


static bool __is_in_list_of_strings(struct list_of_strings * list, const char * str_to_check) {

	assert(list != NULL);

	if (str_to_check == NULL) {
		return false;
	}

	struct list_of_strings * ptr = list;

	while (ptr != NULL) {
		if (strcmp(ptr->str, str_to_check)==0) {
			return true;
		}
		ptr = ptr->next;
	}

	return false;


}

static bool __is_in_fl_esclusion_list(const char * class_name) {

	return __is_in_list_of_strings(fl_exclusion_list, class_name);

}


static void JNICALL
jvmti_callback_class_file_load (
		jvmtiEnv * jvmti, JNIEnv * jni,
		jclass class_being_redefined, jobject loader,
		const char * class_name, jobject protection_domain,
		jint class_byte_count, const unsigned char * class_bytes,
		jint * new_class_byte_count, unsigned char ** new_class_bytes
) {

	nLoadedClasses++;

	struct thread_info info = INIT_THREAD_INFO;
	rdexec {
		__thread_info_init (jvmti, jni, &info);

		rdatiprefix (
				&info, "processing %s (%ld bytes)\n",
				__safe (class_name), (long) class_byte_count
		);
	}


	//
	// Avoid instrumenting the bypass check class.
	//
	if (class_name != NULL && strcmp (class_name, BPC_CLASS_NAME) == 0) {
		rdatiprintf (&info, "ignored %s (bypass check class)\n", class_name);
		return;
	}

	//
	// Force loading of the super class or the interface classes. This is
	// only used when the VM is out of the PRIMORDIAL phase, which allows
	// JNI calls to be made.

	rdatiprintf (&info, "Loaded class %s. \n", __safe (class_name));

	jint loader_tag = DISL_LOADER_UNKNOWN; //Loader unknown by default

	struct type_and_classloader * tc_list_root = NULL;


	if (agent_config.force_superclass || agent_config.force_interfaces) {

		if (jvm_is_started) { //Out of the PRIMORDIAL phase.
			loader_tag = __set_or_get_classloader_tag(jvmti, loader);

			if (__is_in_fl_esclusion_list(class_name)) { //Skips force-loading of classes contained in the dedicated exclusion list
				rdatiprintf (&info, "FL:: Skipped force-loading of class %s with loader %d\n", __safe (class_name), loader_tag);
			} else {
				rdatiprintf (&info, "FL:: Forcing lookup of supertypes of %s with loader %d\n", __safe (class_name), loader_tag);
				tc_list_root = __force_classes (jvmti, jni, class_name, class_bytes, class_byte_count, loader, loader_tag);
			}
		} else {
			rdatiprintf (&info, "FL:: VM still primordial, skipping lookup of supertypes of %s\n", __safe (class_name));
			//We assume that in the primordial phase, classes can be loaded only with the the bootstrap class loader.
			loader_tag=0;
		}
	}
	else {

		loader_tag = DISL_LOADER_DISABLED;
	}

	assert(loader_tag != DISL_LOADER_UNKNOWN);

	//
	// Instrument the class and if changed by the server, provide the
	// code to the JVM in its own memory.
	//

	jvmtiClassDefinition class_def = {
			.class_byte_count = class_byte_count,
			.class_bytes = class_bytes,
	};

	bool class_changed = __instrument_class (
			agent_code_flags, class_name, &class_def, loader_tag, tc_list_root
	);

	__free_type_and_classloader_list(tc_list_root);

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

		rdatiprintf (
				&info, "redefined %s (%ld bytes)\n",
				__safe (class_name), (long) class_def.class_byte_count
		);
	} else {
		rdatiprintf (&info, "loaded %s (unmodified)\n", __safe (class_name));
	}


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
		rdatiprefix (&info, "vm_init (the VM has been initialized)\n");
	}

	//
	// Redefine the bypass check class. If dynamic bypass is required, use
	// a class that honors the dynamic bypass state for the current thread.
	// Otherwise use a class that disables bypassing instrumented code.
	//
	jvmtiClassDefinition * bpc_classdef;
	if (agent_config.bypass_mode == BYPASS_MODE_DYNAMIC) {
		rdatiprintf (&info, "redefining BypassCheck for dynamic bypass\n");
		bpc_classdef = &dynamic_BypassCheck_classdef;
	} else {
		rdatiprintf (&info, "redefining BypassCheck to disable bypass\n");
		bpc_classdef = &never_BypassCheck_classdef;
	}

	jvmti_redefine_class (jvmti, jni, BPC_CLASS_NAME, bpc_classdef);

	rdexec {
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
		rdatiprefix (&info, "vm_death (the VM is shutting down)\n");
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

	config->print_loaded_classes = jvmti_get_system_property_bool (
			jvmti, DISL_PRINT_CLASS_LOADED, DISL_PRINT_CLASS_LOADED_DEFAULT
	);


	//
	// Get string values from system properties
	//

	char * exclusionFLfile = jvmti_get_system_property_string (
			jvmti, DISL_FL_EXCLUSION_FILE, DISL_FL_EXCLUSION_FILE_DEFAULT
	);

	config->fl_exclusion_file = exclusionFLfile;

	//
	// Configuration summary. Current thread does not exist yet.
	//
	rdexec {
		rdaprefix ("bypass mode: %s\n", values [bypass_index]);
		rdaprefix ("split methods: %d\n", config->split_methods);
		rdaprefix ("catch exceptions: %d\n", config->catch_exceptions);
		rdaprefix ("force superclass: %d\n", config->force_superclass);
		rdaprefix ("force interfaces: %d\n", config->force_interfaces);
		rdaprefix ("force loading exclusion file: %s\n", config->fl_exclusion_file);
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

//This implementation does not insert empty strings
static struct list_of_strings * __insert_in_string_list(struct list_of_strings * prev_node, const char * str_to_insert) {

	if (str_to_insert == NULL || strcmp(str_to_insert,"")==0) {
		return prev_node;
	}

	struct list_of_strings * this_node = malloc(sizeof(struct list_of_strings *));
	this_node->str = (char *) malloc(strlen(str_to_insert)+1);
	strcpy((char *)this_node->str,str_to_insert);
	this_node->next = NULL;

	if (prev_node != NULL) {
		prev_node->next = this_node;
	}

	return this_node;

}




static struct list_of_strings * __build_fl_exclusion_list (struct config * config) {

	struct list_of_strings * root = NULL;

	if ((config->force_superclass || config->force_interfaces) && strcmp(config->fl_exclusion_file,DISL_FL_EXCLUSION_FILE_DEFAULT)!=0) {

		FILE * file = fopen(config->fl_exclusion_file, "r");
		if (file == NULL) {
			warn("force-loading exclusion file not found. Proceeding with no exclusions");
			return root;
		}

		char line[5096];

		struct list_of_strings * ptr = NULL;

		while (fgets(line, sizeof(line), file)) {

			// remove newline
			int len = strlen(line);
			if( line[len-1] == '\n' ) {
				line[len-1] = '\0';
			}
			//

			ptr = __insert_in_string_list(ptr,line);
			if (root == NULL) {
				root = ptr;
			}
		}

		fclose(file);

		return root;

	}


	return root;

}

/************************************
/******** DiSL controller ***********
/************************************/

void send_control(JNIEnv * jni, jstring snippetName, ControllerAction action) {

	uint8_t * snippetStr = (*jni)->GetStringUTFChars(jni, snippetName, NULL);
	check_error(snippetStr == NULL, "Cannot get string from Java");

	ControllerRequest request = CONTROLLER_REQUEST__INIT;
	request.action = action;
	request.snippetname = snippetStr;

	size_t send_size = controller_request__get_packed_size (&request);
	void * send_buffer = malloc (send_size);
	assert (send_buffer != NULL);

	controller_request__pack (&request, send_buffer);
	struct connection * conn = network_acquire_connection ();
	message_send (conn, DISL_MESSAGE_CONTROLLER, send_buffer, send_size);

	//

	void * recv_buffer;
	size_t recv_size = message_recv (conn, &recv_buffer);
	network_release_connection (conn);

	ControllerResponse * response = controller_response__unpack (NULL, recv_size, recv_buffer);
	assert (response != NULL);
	free (recv_buffer);

	(* jni)->ReleaseStringUTFChars (jni, snippetName, snippetStr);

	if (response->result == CONTROLLER_RESULT__KO) {
		fprintf (
			stderr, "%sinstrumentation server error:\n%s\n",
			ERROR_PREFIX, response->errormessage
		);

		exit (ERROR_SERVER);
	}

}

JNIEXPORT void JNICALL VISIBLE
Java_ch_usi_dag_disl_dislcontroller_DiSLController_deploy
(JNIEnv * jni, jclass this_class, jstring snippetName) {

	send_control(jni, snippetName, CONTROLLER_ACTION__DEPLOY);
	return;
}

JNIEXPORT void JNICALL VISIBLE
Java_ch_usi_dag_disl_dislcontroller_DiSLController_undeploy
(JNIEnv * jni, jclass this_class, jstring snippetName) {

	send_control(jni, snippetName, CONTROLLER_ACTION__UNDEPLOY);
	return;
}

JNIEXPORT void JNICALL VISIBLE
Java_ch_usi_dag_disl_dislcontroller_DiSLController_retransformClass
(JNIEnv * jni, jclass this_class, jclass klass) {

	(*jvmti)->RetransformClasses(jvmti, 1, &klass);
}


/////////////////////////

JNIEXPORT jint JNICALL VISIBLE
Agent_OnLoad (JavaVM * jvm, char * options, void * reserved) {
	jvmti = __get_jvmti (jvm);

	// add capabilities
	jvmtiCapabilities caps = {
			.can_redefine_classes = 1,
			.can_generate_all_class_hook_events = 1,
			.can_tag_objects = 1, //Added capability for supporting classloader tag
			.can_retransform_classes = 1, //Needed for the Deploy API
			.can_retransform_any_class = 1 //Needed for the Deploy API
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
	
	// build exclusion list for force-loading
	fl_exclusion_list = __build_fl_exclusion_list(&agent_config);

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

	__free_list_of_strings(fl_exclusion_list);

	if (agent_config.print_loaded_classes) {
		printf("DISL-AGENT: classes loaded: %d", nLoadedClasses);
	}

	rdaprintf ("agent unloaded, closing connections\n");

	//
	// Just close all the connections.
	//
	network_fini ();
}
