// ===============================
// Configuration (tune these)
// ===============================

#define MAX_BINS 32
#define MAX_SPACES_PER_BIN 64

// ===============================
// Data structures
// ===============================

typedef struct {
    float w, h, d;
} Box;

typedef struct {
    float x, y, z;
    float w, h, d;
} Space;

// ===============================
// Kernel
// ===============================

__kernel void guillotine_first_fit(
    __global const Box* boxes,     // All boxes
    __global const int* orders,    // Flattened permutations
    __global float* scores,        // One score per order
    int num_boxes,

    float bin_w,
    float bin_h,
    float bin_d
) {
    int gid = get_global_id(0);

    // ----------------------------------
    // Per-work-item private state
    // ----------------------------------

    Space spaces[MAX_BINS * MAX_SPACES_PER_BIN];
    int space_count[MAX_BINS];

    float used_volume[MAX_BINS];
    int bins_used = 1;

    // ----------------------------------
    // Initialize first bin
    // ----------------------------------

    space_count[0] = 1;
    used_volume[0] = 0.0f;

    spaces[0] = (Space){
        0.0f, 0.0f, 0.0f,
        bin_w, bin_h, bin_d
    };

    for (int b = 1; b < MAX_BINS; b++) {
        space_count[b] = 0;
        used_volume[b] = 0.0f;
    }

    // ----------------------------------
    // Packing loop
    // ----------------------------------

    for (int i = 0; i < num_boxes; i++) {

        int box_id = orders[gid * num_boxes + i];
        Box box = boxes[box_id];

        int placed = 0;

        // First-fit over bins
        for (int b = 0; b < bins_used && !placed; b++) {

            int base = b * MAX_SPACES_PER_BIN;

            for (int s = 0; s < space_count[b]; s++) {

                Space sp = spaces[base + s];

                // Fit test
                if (box.w <= sp.w &&
                    box.h <= sp.h &&
                    box.d <= sp.d) {

                    placed = 1;

                    // ----------------------------------
                    // Account used volume
                    // ----------------------------------

                    used_volume[b] += box.w * box.h * box.d;

                    // ----------------------------------
                    // Remove used space (swap with last)
                    // ----------------------------------

                    space_count[b]--;
                    spaces[base + s] =
                        spaces[base + space_count[b]];

                    // ----------------------------------
                    // Guillotine splits
                    // ----------------------------------

                    if (space_count[b] + 3 > MAX_SPACES_PER_BIN) {
                        scores[gid] = -1.0f;
                        return;
                    }

                    // Right space
                    if (sp.w - box.w > 0.0f) {
                        spaces[base + space_count[b]++] = (Space){
                            sp.x + box.w,
                            sp.y,
                            sp.z,
                            sp.w - box.w,
                            sp.h,
                            sp.d
                        };
                    }

                    // Top space
                    if (sp.h - box.h > 0.0f) {
                        spaces[base + space_count[b]++] = (Space){
                            sp.x,
                            sp.y + box.h,
                            sp.z,
                            box.w,
                            sp.h - box.h,
                            sp.d
                        };
                    }

                    // Front space
                    if (sp.d - box.d > 0.0f) {
                        spaces[base + space_count[b]++] = (Space){
                            sp.x,
                            sp.y,
                            sp.z + box.d,
                            box.w,
                            box.h,
                            sp.d - box.d
                        };
                    }

                    break;
                }
            }
        }

        // ----------------------------------
        // Create new bin if needed
        // ----------------------------------

        if (!placed) {

            if (bins_used >= MAX_BINS) {
                scores[gid] = -1.0f;
                return;
            }

            int b = bins_used++;
            int base = b * MAX_SPACES_PER_BIN;

            used_volume[b] = box.w * box.h * box.d;
            space_count[b] = 0;

            // Right
            if (bin_w - box.w > 0.0f) {
                spaces[base + space_count[b]++] = (Space){
                    box.w, 0.0f, 0.0f,
                    bin_w - box.w,
                    box.h,
                    box.d
                };
            }

            // Top
            if (bin_h - box.h > 0.0f) {
                spaces[base + space_count[b]++] = (Space){
                    0.0f, box.h, 0.0f,
                    box.w,
                    bin_h - box.h,
                    box.d
                };
            }

            // Front
            if (bin_d - box.d > 0.0f) {
                spaces[base + space_count[b]++] = (Space){
                    0.0f, 0.0f, box.d,
                    box.w,
                    box.h,
                    bin_d - box.d
                };
            }
        }
    }

    // ----------------------------------
    // Scoring
    // Sum used volume except last bin
    // ----------------------------------

    float score = 0.0f;
    for (int b = 0; b < bins_used - 1; b++) {
        score += used_volume[b];
    }

    scores[gid] = score;
}
