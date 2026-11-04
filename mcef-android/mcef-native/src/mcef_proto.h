#ifndef MCEF_PROTO_H
#define MCEF_PROTO_H

#include <stdint.h>
#include <stddef.h>

#define MCEF_PORT 39090
#define MCEF_MAGIC "MCEF"
#define MCEF_VERSION 1

/* ---- C -> H ---- */
#define OP_CREATE_BROWSER   0x0101
#define OP_DESTROY          0x0102
#define OP_LOAD_URL         0x0103
#define OP_RESIZE           0x0104
#define OP_GO_BACK          0x0110
#define OP_GO_FORWARD       0x0111
#define OP_RELOAD           0x0112
#define OP_STOP             0x0113
#define OP_MOUSE_MOVE       0x0120
#define OP_MOUSE_DOWN       0x0121
#define OP_MOUSE_UP         0x0122
#define OP_MOUSE_WHEEL      0x0123
#define OP_KEY              0x0130
#define OP_SET_FOCUS        0x0140
#define OP_IME_COMPOSE      0x0141
#define OP_IME_COMMIT       0x0142
#define OP_IME_CANCEL       0x0143
#define OP_EVAL_JS          0x0150
#define OP_SET_ZOOM         0x0151
#define OP_SET_BACKGROUND   0x0152
#define OP_COOKIE_SET       0x0160
#define OP_COOKIE_FLUSH     0x0162
#define OP_PING             0x0190
#define OP_QUERY_REPLY      0x0160
#define OP_SCHEME_RESPONSE  0x0161

/* ---- H -> C ---- */
#define EV_PAINT            0x0201
#define EV_POPUP_PAINT      0x0202
#define EV_POPUP_SHOW       0x0203
#define EV_POPUP_SIZE       0x0204
#define EV_CURSOR_CHANGE    0x0210
#define EV_LOAD_STATE       0x0220
#define EV_ADDRESS_CHANGE   0x0221
#define EV_TITLE_CHANGE     0x0222
#define EV_STATUS_MESSAGE   0x0223
#define EV_CONSOLE_MESSAGE  0x0224
#define EV_BEFORE_POPUP     0x0225
#define EV_JS_RESULT        0x0226
#define EV_AFTER_CREATED    0x0227
#define EV_FOCUSED_NODE     0x0228
#define EV_SELECTION        0x0229
#define EV_RENDER_TERMINATED 0x022A
#define EV_DOWNLOAD_BEGIN   0x022B
#define EV_CONTEXT_MENU     0x022C
#define EV_PONG             0x0230
#define EV_ACK              0x0231
#define EV_QUERY            0x0240
#define EV_SCHEME_REQUEST   0x0241

#define MCEF_OK 0
#define MCEF_ERR_CLOSED (-5)
#define MCEF_ERR_INTERNAL (-7)

typedef struct {
    uint8_t *data;
    size_t   len;
    size_t   cap;
} mcef_buf;

void mcef_buf_init(mcef_buf *b);
void mcef_buf_free(mcef_buf *b);
void mcef_buf_u8(mcef_buf *b, uint8_t v);
void mcef_buf_u16(mcef_buf *b, uint16_t v);
void mcef_buf_u32(mcef_buf *b, uint32_t v);
void mcef_buf_i32(mcef_buf *b, int32_t v);
void mcef_buf_i64(mcef_buf *b, int64_t v);
void mcef_buf_f32(mcef_buf *b, float v);
void mcef_buf_f64(mcef_buf *b, double v);
void mcef_buf_str(mcef_buf *b, const char *s);
void mcef_buf_bytes(mcef_buf *b, const void *data, size_t n);

/* 只读游标 */
typedef struct {
    const uint8_t *data;
    size_t len;
    size_t pos;
} mcef_reader;

uint8_t  mcef_rd_u8(mcef_reader *r);
uint16_t mcef_rd_u16(mcef_reader *r);
uint32_t mcef_rd_u32(mcef_reader *r);
int32_t  mcef_rd_i32(mcef_reader *r);
int64_t  mcef_rd_i64(mcef_reader *r);
float    mcef_rd_f32(mcef_reader *r);
/* 返回 malloc 出来的 UTF-8 串，调用方 free；越界返回 NULL */
char    *mcef_rd_str(mcef_reader *r);

typedef struct mcef_session mcef_session;

/* 由 jni_bridge.c 实现：把事件派发到 Java */
void mcef_dispatch(mcef_session *s, uint16_t type, uint32_t id, mcef_reader *r, int32_t *out_err);

mcef_session *mcef_connect(void);
void          mcef_close(mcef_session *s);
int           mcef_send(mcef_session *s, uint16_t type, uint16_t flags, uint32_t id, const mcef_buf *payload);
int           mcef_next_id(mcef_session *s);
int           mcef_is_open(mcef_session *s);

void          mcef_set_user(mcef_session *s, void *u);
void         *mcef_get_user(mcef_session *s);
uint8_t      *mcef_frame_scratch(mcef_session *s, size_t need);

#define EV_DISCONNECTED 0x02FE

#endif
