#include "common.h"
#include "msgchannel.h"
#include "connection.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

#ifndef MINGW
#include <sys/uio.h>
#endif

//

#ifdef MINGW

static inline ssize_t
__send (struct connection * conn, void * header, const size_t header_size, void * body, const size_t body_size) {
	ssize_t sent = connection_send_full (conn, header, header_size);
	sent += connection_send_full (conn, body, body_size);
	return sent;
}

#else

static inline ssize_t
__send (struct connection * conn, void * header, const size_t header_size, void * body, const size_t body_size) {
	struct iovec iovs [] = {
		{ .iov_base = header, .iov_len = header_size },
		{ .iov_base = body, .iov_len = body_size },
	};

	return connection_send_iov_full (conn, &iovs [0], sizeof_array (iovs));
}

#endif /* !MINGW */


/**
 * Sends the given message to the remote instrumentation server
 * via the given connection. Returns the number of bytes sent.
 */
ssize_t
message_send (struct connection * conn, void * message, const size_t message_size) {
	assert (conn != NULL);
	assert (message != NULL);

	ldebug ("sending message: %lu bytes", message_size);

	//

	uint32_t message_size_data = htonl (message_size);
	size_t header_size = sizeof (message_size_data);
	ssize_t sent = __send (conn, &message_size_data, header_size, message, message_size);
	assert (sent == (ssize_t) (header_size + message_size));

	//

	debug (", sent %ld bytes\n", sent);
	return sent;
}



static inline uint8_t *
__alloc_buffer (size_t len) {
	//
	// Allocate a buffer with an extra (zeroed) byte, but only if the
	// requested buffer length is greater than zero. Return NULL otherwise.
	//
	if (len == 0) {
		return NULL;
	}

	//

	uint8_t * buf = (uint8_t *) malloc (len + 1);
	check_error (buf == NULL, "failed to allocate buffer");

	buf [len] = '\0';
	return buf;
}


/**
 * Receives a message from the remote instrumentation server.
 */
ssize_t
message_recv (struct connection * conn, void ** message_ptr) {
	assert (conn != NULL);
	assert (message_ptr != NULL);

	ldebug ("receiving message: ");

	//
	// First receive the size of the message.
	// Then receive the message itself.
	//
	uint32_t message_size_data;
	connection_recv_full (conn, &message_size_data, sizeof (message_size_data));
	size_t message_size = ntohl (message_size_data);
	debug ("expecting %ld bytes", message_size);

	//
	// The message body can be completely empty. In this case,
	// the buffer allocator will just return NULL and we avoid
	// reading from the socket. We will set the message pointer
	// to NULL and return zero as message size.
	//
	uint8_t * message = __alloc_buffer (message_size);
	connection_recv_full (conn, message, message_size);

	//
	// Return the message only after the whole message was read.
	//
	debug ("... done\n");
	* message_ptr = (void *) message;
	return message_size;
}
