# CPU Solver Guide

This document explains how to use and create CPU-based bin packing solvers using the `SolverInterface`.

## Using Existing CPU Solvers

The library includes several CPU-based solvers:

- **FirstFit3D** - First-fit algorithm using Binary Space Partitioning (BSP) in 3D
- **FirstFit2D** - First-fit algorithm using BSP in 2D
- **BestFit3D** - Best-fit algorithm using BSP in 3D
- **BestFitEMS** - Best-fit algorithm using Empty Maximal Spaces (EMS)

### Example Usage

```java
import com.binpacker.lib.solver.cpusolvers.BestFit3D;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.common.*;

// Create a bin template
Bin bin = new Bin(0, 30, 30, 30); // width, height, depth

// Configure solver properties
List<Integer> rotationAxes = Arrays.asList(0, 1, 2); // Allow rotation on X, Y, Z
SolverProperties properties = new SolverProperties(
    bin,
    false,           // growingBin
    "x",             // growAxis (if growingBin is true)
    rotationAxes,    // allowed rotation axes
    null             // OpenCL device (not needed for CPU solvers)
);

// Create and initialize solver
BestFit3D solver = new BestFit3D();
solver.init(properties);

// Load boxes (from CSV or create manually)
List<Box> boxes = new ArrayList<>();
boxes.add(new Box(new Point3f(0, 0, 0), new Point3f(7, 6, 7)));
boxes.add(new Box(new Point3f(0, 0, 0), new Point3f(9, 7, 6)));
// ... add more boxes

// Solve
List<List<Box>> result = solver.solve(boxes);

// result contains bins, each bin contains placed boxes with positions
for (int i = 0; i < result.size(); i++) {
    System.out.println("Bin " + i + ": " + result.get(i).size() + " boxes");
    for (Box box : result.get(i)) {
        System.out.printf("  Box %d at (%.1f, %.1f, %.1f) size (%.1f, %.1f, %.1f)%n",
            box.id, box.position.x, box.position.y, box.position.z,
            box.size.x, box.size.y, box.size.z);
    }
}

// Clean up
solver.release();
```

## Creating Your Own CPU Solver

To create a custom CPU solver, implement the `SolverInterface` interface.

### Interface Definition

```java
package com.binpacker.lib.solver.cpusolvers;

import java.util.List;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.common.SolverProperties;

public interface SolverInterface {
    void init(SolverProperties properties);
    List<List<Box>> solve(List<Box> boxes);
    void release();
}
```

### Method Descriptions

#### `void init(SolverProperties properties)`

Called once to initialize the solver with configuration.

**Parameters:**
- `properties.bin` - Template bin with dimensions (also includes `weight` field)
- `properties.growingBin` - If true, bin can grow indefinitely along one axis
- `properties.growAxis` - Which axis to grow ("x", "y", or "z")
- `properties.rotationAxes` - List of allowed rotation axes (0=X, 1=Y, 2=Z)
- `properties.weight` - Weight limit for the bin (defaults to 0)
- `properties.openCLDevice` - OpenCL device (can be null for CPU solvers)

#### `List<List<Box>> solve(List<Box> boxes)`

Performs the packing algorithm.

**Parameters:**
- `boxes` - List of boxes to pack (input boxes have size only, no position)

**Returns:**
- List of bins, where each bin is a list of boxes with positions assigned

**Important:**
- Input boxes have `position` at (0,0,0) and only `size` is meaningful
- Input boxes may have a `weight` field set (defaults to 0)
- Output boxes must have both `position` and `size` set
- Each box should maintain its original `id` and `weight` fields
- Create new `Bin` instances as needed based on `binTemplate`

#### `void release()`

Called to release any resources. For simple CPU solvers, this is typically empty.

### Implementation Example

Here's a simple example implementing a basic first-fit solver:

```java
package com.binpacker.lib.solver.cpusolvers;

import java.util.ArrayList;
import java.util.List;
import com.binpacker.lib.common.*;
import com.binpacker.lib.solver.common.PlacementUtils;
import com.binpacker.lib.solver.common.SolverProperties;

public class SimpleFirstFit implements SolverInterface {
    
    private Bin binTemplate;
    private boolean growingBin;
    private String growAxis;
    private List<Integer> rotationAxes;
    
    @Override
    public void init(SolverProperties properties) {
        this.binTemplate = properties.bin;
        this.growingBin = properties.growingBin;
        this.growAxis = properties.growAxis;
        this.rotationAxes = properties.rotationAxes;
    }
    
    @Override
    public List<List<Box>> solve(List<Box> boxes) {
        List<Bin> activeBins = new ArrayList<>();
        List<List<Box>> result = new ArrayList<>();
        
        // Create first bin
        activeBins.add(new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d));
        
        // Try to place each box
        for (Box box : boxes) {
            boolean placed = false;
            
            // Try existing bins
            for (Bin bin : activeBins) {
                for (int i = 0; i < bin.freeSpaces.size(); i++) {
                    Space space = bin.freeSpaces.get(i);
                    Box fittedBox = PlacementUtils.findFit(box, space, rotationAxes);
                    
                    if (fittedBox != null) {
                        PlacementUtils.placeBoxBSP(fittedBox, bin, i);
                        placed = true;
                        break;
                    }
                }
                if (placed) break;
            }
            
            // If not placed, create new bin
            if (!placed) {
                Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
                activeBins.add(newBin);
                
                Box fittedBox = PlacementUtils.findFit(box, newBin.freeSpaces.get(0), rotationAxes);
                if (fittedBox != null) {
                    PlacementUtils.placeBoxBSP(fittedBox, newBin, 0);
                } else {
                    System.err.println("Box too big for bin: " + box);
                }
            }
        }
        
        // Convert bins to result format
        for (Bin bin : activeBins) {
            result.add(bin.boxes);
        }
        
        return result;
    }
    
    @Override
    public void release() {
        // No resources to release
    }
}
```

### Key Helper Methods

The `PlacementUtils` class provides useful methods:

- **`findFit(Box box, Space space, List<Integer> rotationAxes)`** - Attempts to fit a box into a space, trying rotations if allowed. Returns a new Box with position and rotation applied, or null if it doesn't fit.

- **`placeBoxBSP(Box box, Bin bin, int spaceIndex)`** - Places a box into a bin at the given space index and updates the bin's free spaces using Binary Space Partitioning.

- **`placeBoxEMS(Box box, Bin bin, int spaceIndex)`** - Places a box into a bin using Empty Maximal Spaces algorithm.

### Tips

- Store configuration from `init()` in instance fields
- Create new `Bin` instances using `binTemplate` dimensions
- Use `PlacementUtils.findFit()` to handle box rotation logic
- Maintain box IDs from input to output
- Return bins with boxes that have both position and size set
- For growing bins, update final bin dimension after placing all boxes
