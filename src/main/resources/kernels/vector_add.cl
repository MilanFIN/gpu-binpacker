__kernel void vector_add(__global const float *a,
                         __global const float *b,
                         __global float *c,
                         __global const uint *nPtr)
{
    uint gid = get_global_id(0);
    uint n = nPtr[0];

    if (gid < n)
        c[gid] = a[gid] + b[gid];
}