#include "jnicetouch.h"

#include <assert.h>
#include <stddef.h>

_Static_assert(sizeof(jnt_event_v1) == 128, "jnt_event_v1 size changed");
_Static_assert(offsetof(jnt_event_v1, struct_size) == 0, "bad struct_size offset");
_Static_assert(offsetof(jnt_event_v1, sequence_id) == 24, "bad sequence_id offset");
_Static_assert(offsetof(jnt_event_v1, screen_x) == 56, "bad screen_x offset");
_Static_assert(offsetof(jnt_event_v1, pressure_stage) == 120, "bad pressure_stage offset");

int main(void) {
    assert(jnt_abi_version() == JNT_ABI_VERSION_1);
    assert(jnt_event_struct_size(JNT_ABI_VERSION_1) == sizeof(jnt_event_v1));
    assert(jnt_event_struct_size(999) == 0);
    assert((jnt_capabilities_v1() & JNT_CAP_PRECISE_SCROLL) != 0);
    assert(jnt_install_v1(UINT64_C(1) << 63) == JNT_STATUS_UNSUPPORTED);
    assert(jnt_wait_and_drain_v1(NULL, 1, 0) == 0);
    assert(jnt_wait_and_drain_v1((jnt_event_v1[1]){{0}}, 0, 0) == 0);
    return 0;
}
