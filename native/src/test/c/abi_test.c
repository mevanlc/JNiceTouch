#include "jnicetouch.h"

#include <assert.h>
#include <stddef.h>

_Static_assert(sizeof(jnt_event_v1) == 128, "jnt_event_v1 size changed");
_Static_assert(_Alignof(jnt_event_v1) == 8, "jnt_event_v1 alignment changed");
_Static_assert(offsetof(jnt_event_v1, struct_size) == 0, "bad struct_size offset");
_Static_assert(offsetof(jnt_event_v1, abi_version) == 4, "bad abi_version offset");
_Static_assert(offsetof(jnt_event_v1, kind) == 6, "bad kind offset");
_Static_assert(offsetof(jnt_event_v1, flags) == 8, "bad flags offset");
_Static_assert(offsetof(jnt_event_v1, phase) == 12, "bad phase offset");
_Static_assert(offsetof(jnt_event_v1, momentum_phase) == 16, "bad momentum offset");
_Static_assert(offsetof(jnt_event_v1, reserved_0) == 20, "bad reserved offset");
_Static_assert(offsetof(jnt_event_v1, sequence_id) == 24, "bad sequence_id offset");
_Static_assert(offsetof(jnt_event_v1, timestamp_ns) == 32, "bad timestamp offset");
_Static_assert(offsetof(jnt_event_v1, modifier_flags) == 40, "bad modifiers offset");
_Static_assert(offsetof(jnt_event_v1, window_number) == 48, "bad window offset");
_Static_assert(offsetof(jnt_event_v1, screen_x) == 56, "bad screen_x offset");
_Static_assert(offsetof(jnt_event_v1, screen_y) == 64, "bad screen_y offset");
_Static_assert(offsetof(jnt_event_v1, delta_x) == 72, "bad delta_x offset");
_Static_assert(offsetof(jnt_event_v1, delta_y) == 80, "bad delta_y offset");
_Static_assert(offsetof(jnt_event_v1, magnification) == 88, "bad magnification offset");
_Static_assert(offsetof(jnt_event_v1, rotation_degrees) == 96, "bad rotation offset");
_Static_assert(offsetof(jnt_event_v1, pressure) == 104, "bad pressure offset");
_Static_assert(offsetof(jnt_event_v1, stage_transition) == 112, "bad stage offset");
_Static_assert(offsetof(jnt_event_v1, pressure_stage) == 120, "bad pressure_stage offset");
_Static_assert(offsetof(jnt_event_v1, pressure_behavior) == 124, "bad behavior offset");

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
