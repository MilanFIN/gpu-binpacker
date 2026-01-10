# 3D binpacker with gpu acceleration and a genetic algorithm

A to be modular, extendable, multi stage, multi heuristic 3d binpacker.
The packing consists of two stages: the packing heuristic and an optimizer that 
searches for a better solution by iterating over different packing orders.

![demo packing result](https://raw.githubusercontent.com/MilanFIN/GA-binpacker/refs/heads/main/images/3dbin.png)

## Packing algorithms

Implements  first fit and best fit packing heuristics in both binary space partition and empty maximal spaces flavors.
The packing algorithm can be optionally run on the GPU, so the optimizer can run multiple packing attempts in parallel for faster iteration.

## Optimizer

A genetic algorithm that attempts to find an optimal packing order for use with the selected packing algorithm. 
Performs crossover and mutations to generate new packing orders based on the best scoring results of the previous generation. 
Indivdual packing attempts will happen in parallel both on the CPU and GPU depending on the chosen algorithm

## OpenCL

Uses [JOCL](https://github.com/gpu/JOCL). 

Make sure the opencl device is available with `clinfo`
and that `libOpenCL.so` is visible, eg. in `/usr/lib/x86_64-linux-gnu/libOpenCL.so`
