#ifndef _MSGCHANNEL_H_
#define _MSGCHANNEL_H_

#include "connection.h"

ssize_t message_send (struct connection * conn, void * message, const size_t message_size);
ssize_t message_recv (struct connection * conn, void ** message_ptr);

#endif /* _MSGCHANNEL_H_ */
