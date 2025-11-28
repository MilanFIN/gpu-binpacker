# GA-binpacker

A to be modular, extendable, multi stage, multi heuristic 3d binpacker.
The packing consists of two stages: the packing heuristic and an optimizer that 
searches for a better solution by iterating over different packing orders.

![demo packing result](https://raw.githubusercontent.com/MilanFIN/GA-binpacker/refs/heads/main/images/3dbin.png)

## Packing algorithms

Implements basic first fit and best fit packing heuristics in addition
to derived custom algorithms. Custom ones are slower, but produce
better results (>10% difference on the demo)

## Optimizer

A genetic algorithm that attempts to find an optimal packing order
for use with the selected packing algorithm.

Performs crossover and mutations to generate new packing orders
based on the best scoring results of the previous generation.
