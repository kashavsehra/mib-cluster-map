#include "gal_hook.h"

#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int gal_view_insets_parse(unsigned *top, unsigned *bottom, unsigned *left,
                          unsigned *right, const char *value)
{
    unsigned parsed[4];
    const char *cursor = value;
    unsigned i;

    if (top == NULL || bottom == NULL || left == NULL || right == NULL ||
        value == NULL || *value == '\0') return 0;
    for (i = 0u; i < 4u; ++i) {
        char *end;
        unsigned long number;
        errno = 0;
        number = strtoul(cursor, &end, 10);
        if (end == cursor || errno == ERANGE || number > 0x3ffful) return 0;
        parsed[i] = (unsigned)number;
        if (i < 3u) {
            if (*end != ',') return 0;
            cursor = end + 1;
        } else if (*end != '\0') {
            return 0;
        }
    }

    *top = parsed[0];
    *bottom = parsed[1];
    *left = parsed[2];
    *right = parsed[3];
    return 1;
}

int gal_view_insets_select_from_file(gal_secondary_config *config,
                                     const char *path)
{
    char mode[32];
    int fd;
    ssize_t length;

    if (config == NULL || path == NULL || !config->view_insets_enabled) return 0;
    fd = open(path, O_RDONLY);
    if (fd < 0) return errno == ENOENT ? 0 : -2;
    do {
        length = read(fd, mode, sizeof(mode) - 1u);
    } while (length < 0 && errno == EINTR);
    if (length < 0) {
        int saved_errno = errno;
        (void)close(fd);
        errno = saved_errno;
        return -2;
    }
    (void)close(fd);
    if (length <= 0) return -1;

    mode[length] = '\0';
    while (length > 0 && (mode[length - 1] == '\n' || mode[length - 1] == '\r' ||
                          mode[length - 1] == ' ' || mode[length - 1] == '\t')) {
        mode[--length] = '\0';
    }
    if (strcmp(mode, "classic") == 0) {
        config->insets_top = config->classic_insets.top;
        config->insets_bottom = config->classic_insets.bottom;
        config->insets_left = config->classic_insets.left;
        config->insets_right = config->classic_insets.right;
        return 1;
    }
    if (strcmp(mode, "large") == 0) {
        config->insets_top = config->large_insets.top;
        config->insets_bottom = config->large_insets.bottom;
        config->insets_left = config->large_insets.left;
        config->insets_right = config->large_insets.right;
        return 2;
    }
    return -1;
}

void gal_view_insets_scale(unsigned *top, unsigned *bottom, unsigned *left,
                           unsigned *right, unsigned pixel_aspect_ratio_e4)
{
    unsigned band_margin_half;
    unsigned band_top;
    unsigned band_bottom;

    if (top == NULL || bottom == NULL || left == NULL || right == NULL ||
        pixel_aspect_ratio_e4 <= 10000u || pixel_aspect_ratio_e4 > 80000u ||
        *top > 0x3fffu || *bottom > 0x3fffu ||
        *left > 0x3fffu || *right > 0x3fffu) return;

    band_margin_half =
        (480u - 2u * ((2400000u + pixel_aspect_ratio_e4 / 2u) /
                      pixel_aspect_ratio_e4)) / 2u;
    band_top = *top > band_margin_half ? *top - band_margin_half : 0u;
    band_bottom = *bottom > band_margin_half ?
        *bottom - band_margin_half : 0u;
    *top = (band_top * pixel_aspect_ratio_e4 + 5000u) / 10000u;
    *bottom = (band_bottom * pixel_aspect_ratio_e4 + 5000u) / 10000u;
    *left = (*left * pixel_aspect_ratio_e4 + 5000u) / 10000u;
    *right = (*right * pixel_aspect_ratio_e4 + 5000u) / 10000u;
}
