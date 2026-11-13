#include "gal_hook.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static void write_state(const char *path, const char *state)
{
    FILE *file = fopen(path, "w");
    assert(file != NULL);
    assert(fputs(state, file) >= 0);
    assert(fclose(file) == 0);
}

int main(void)
{
    gal_secondary_config config;
    unsigned top = 7u, bottom = 8u, left = 9u, right = 10u;
    char path[128];
    unsigned scaled_top = 100u, scaled_bottom = 120u;
    unsigned scaled_left = 40u, scaled_right = 50u;

    assert(gal_view_insets_parse(&top, &bottom, &left, &right,
                                 "11,12,13,14"));
    assert(top == 11u && bottom == 12u && left == 13u && right == 14u);
    assert(!gal_view_insets_parse(&top, &bottom, &left, &right,
                                  "11,12,13,14x"));
    assert(!gal_view_insets_parse(&top, &bottom, &left, &right,
                                  "11,12,13"));
    assert(!gal_view_insets_parse(&top, &bottom, &left, &right,
                                  "11,12,13,16384"));

    memset(&config, 0, sizeof(config));
    config.view_insets_enabled = 1u;
    config.insets_top = 20u;
    config.insets_bottom = 21u;
    config.insets_left = 22u;
    config.insets_right = 23u;
    config.classic_insets.top = 1u;
    config.classic_insets.bottom = 2u;
    config.classic_insets.left = 3u;
    config.classic_insets.right = 4u;
    config.large_insets.top = 5u;
    config.large_insets.bottom = 6u;
    config.large_insets.left = 7u;
    config.large_insets.right = 8u;
    (void)snprintf(path, sizeof(path), "/tmp/view-insets-test-%ld",
                   (long)getpid());

    assert(gal_view_insets_select_from_file(&config, path) == 0);
    write_state(path, "classic\n");
    assert(gal_view_insets_select_from_file(&config, path) == 1);
    assert(config.insets_top == 1u && config.insets_bottom == 2u &&
           config.insets_left == 3u && config.insets_right == 4u);
    write_state(path, "large\n");
    assert(gal_view_insets_select_from_file(&config, path) == 2);
    assert(config.insets_top == 5u && config.insets_bottom == 6u &&
           config.insets_left == 7u && config.insets_right == 8u);
    write_state(path, "unknown\n");
    assert(gal_view_insets_select_from_file(&config, path) == -1);
    assert(config.insets_top == 5u && config.insets_bottom == 6u);
    assert(remove(path) == 0);

    config.view_insets_enabled = 0u;
    config.insets_top = 20u;
    assert(gal_view_insets_select_from_file(&config, path) == 0);
    assert(config.insets_top == 20u);

    gal_view_insets_scale(&scaled_top, &scaled_bottom, &scaled_left,
                          &scaled_right, 15000u);
    assert(scaled_top == 30u && scaled_bottom == 60u &&
           scaled_left == 60u && scaled_right == 75u);
    return 0;
}
