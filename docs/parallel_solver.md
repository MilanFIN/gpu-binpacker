# Parallel Solver Guide

This document explains how to use and create parallel/GPU-based bin packing solvers using the `ParallelSolverInterface`.

## Overview

Parallel solvers are designed to evaluate multiple box orderings simultaneously using OpenCL. They work in conjunction with genetic algorithms that generate different orderings and need fast fitness evaluation.

## Using Existing Parallel Solvers

The library includes several GPU-accelerated solvers:

- **GPUSolver** with FirstFit kernel - First-fit algorithm on GPU
- **GPUSolver** with BestFit kernel - Best-fit algorithm on GPU
- **GPUSolver** with BestFit EMS kernel - Best-fit with Empty Maximal Spaces on GPU

### Example Usage

```java
import com.binpacker.lib.solver.parallelsolvers.*;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.common.*;
import com.binpacker.lib.ocl.OpenCLDevice;

// Get available OpenCL devices
List<OpenCLDevice> devices = JOCLHelper.getAvailableDevices();
OpenCLDevice device = devices.get(0); // Choose first device

// Create bin template
Bin bin = new Bin(0, 30, 30, 30);

// Configure properties
List<Integer> rotationAxes = Arrays.asList(0, 1, 2);
SolverProperties properties = new SolverProperties(
    bin,
    false,
    "x",
    rotationAxes,
    device  // OpenCL device is required for parallel solvers
);

// Create GPU solver
GPUSolver solver = new GPUSolver(
    "bestfit_complete.cl.template",  // Kernel file
    "guillotine_best_fit",           // Kernel function name
    "BestFit GPU (Parallel)",        // Display name
    new BestFitReference()           // Reference implementation
);

solver.init(properties);

// Prepare boxes and orderings
List<Box> boxes = loadBoxes(); // Load your boxes

// Create different orderings to evaluate
List<List<Integer>> orderings = new ArrayList<>();
orderings.add(Arrays.asList(0, 1, 2, 3, 4)); // Order 1
orderings.add(Arrays.asList(4, 3, 2, 1, 0)); // Order 2
orderings.add(Arrays.asList(2, 0, 4, 1, 3)); // Order 3

// Evaluate all orderings in parallel on GPU
List<Double> scores = solver.solve(boxes, orderings);

// scores[i] contains the packing density for orderings[i]
for (int i = 0; i < scores.size(); i++) {
    System.out.printf("Ordering %d: score = %.2f%n", i, scores.get(i));
}

// Clean up
solver.release();
```

## Creating Your Own Parallel Solver

To create a custom parallel solver, implement the `ParallelSolverInterface` interface.

### Interface Definition

```java
package com.binpacker.lib.solver.parallelsolvers;

import java.util.List;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.solver.common.SolverProperties;

public interface ParallelSolverInterface {
    void init(SolverProperties properties);
    List<Double> solve(List<Box> boxes, List<List<Integer>> orders);
    void release();
}
```

### Method Descriptions

#### `void init(SolverProperties properties)`

Initializes the solver with configuration. This is where you should:
- Store bin dimensions and other properties
- Initialize OpenCL context, program, and kernels
- Allocate any persistent GPU buffers

**Parameters:**
- Same as CPU solver, but `properties.openCLDevice` is typically required

#### `List<Double> solve(List<Box> boxes, List<List<Integer>> orders)`

Evaluates multiple box orderings in parallel.

**Parameters:**
- `boxes` - List of boxes to pack (fixed set)
- `orders` - List of orderings, where each ordering is a list of box indices

**Returns:**
- List of scores/densities, one per ordering
- Higher score = better packing (typically volume utilization)

**Important:**
- All orderings must be evaluated for the same set of boxes
- Return scores in the same order as input `orders`
- Parallel execution should happen on GPU for performance

#### `void release()`

Release OpenCL resources (kernels, memory buffers, context, etc.)

### Implementation Example

Here's a simplified example:

```java
package com.binpacker.lib.solver.parallelsolvers;

import java.util.*;
import org.jocl.*;
import static org.jocl.CL.*;
import com.binpacker.lib.common.*;
import com.binpacker.lib.solver.common.SolverProperties;

public class CustomParallelSolver implements ParallelSolverInterface {
    
    private cl_context context;
    private cl_command_queue queue;
    private cl_program program;
    private cl_kernel kernel;
    private Bin binTemplate;
    
    @Override
    public void init(SolverProperties properties) {
        this.binTemplate = properties.bin;
        
        // Initialize OpenCL
        int platformIndex = properties.openCLDevice.platformIndex;
        int deviceIndex = properties.openCLDevice.deviceIndex;
        
        // Get platform and device
        cl_platform_id[] platforms = new cl_platform_id[1];
        clGetPlatformIDs(1, platforms, null);
        
        cl_device_id[] devices = new cl_device_id[1];
        clGetDeviceIDs(platforms[platformIndex], CL_DEVICE_TYPE_ALL, 
                       1, devices, null);
        
        // Create context and queue
        context = clCreateContext(null, 1, devices, null, null, null);
        queue = clCreateCommandQueue(context, devices[deviceIndex], 0, null);
        
        // Load and compile kernel
        String kernelSource = loadKernelSource("my_kernel.cl");
        program = clCreateProgramWithSource(context, 1, 
                                           new String[]{kernelSource}, null, null);
        clBuildProgram(program, 0, null, null, null, null);
        kernel = clCreateKernel(program, "my_kernel_function", null);
    }
    
    @Override
    public List<Double> solve(List<Box> boxes, List<List<Integer>> orders) {
        int numBoxes = boxes.size();
        int numOrders = orders.size();
        
        // 1. Prepare box data (flatten to float array)
        float[] boxData = new float[numBoxes * 3];
        for (int i = 0; i < numBoxes; i++) {
            boxData[i * 3 + 0] = boxes.get(i).size.x;
            boxData[i * 3 + 1] = boxes.get(i).size.y;
            boxData[i * 3 + 2] = boxes.get(i).size.z;
        }
        
        // 2. Prepare order data (flatten to int array)
        int[] orderData = new int[numOrders * numBoxes];
        for (int i = 0; i < numOrders; i++) {
            for (int j = 0; j < numBoxes; j++) {
                orderData[i * numBoxes + j] = orders.get(i).get(j);
            }
        }
        
        // 3. Create GPU buffers
        cl_mem boxesMem = clCreateBuffer(context, 
            CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_float * boxData.length, Pointer.to(boxData), null);
            
        cl_mem ordersMem = clCreateBuffer(context,
            CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_int * orderData.length, Pointer.to(orderData), null);
            
        cl_mem scoresMem = clCreateBuffer(context,
            CL_MEM_WRITE_ONLY,
            Sizeof.cl_float * numOrders, null, null);
        
        // 4. Set kernel arguments
        int arg = 0;
        clSetKernelArg(kernel, arg++, Sizeof.cl_mem, Pointer.to(boxesMem));
        clSetKernelArg(kernel, arg++, Sizeof.cl_mem, Pointer.to(ordersMem));
        clSetKernelArg(kernel, arg++, Sizeof.cl_mem, Pointer.to(scoresMem));
        clSetKernelArg(kernel, arg++, Sizeof.cl_int, Pointer.to(new int[]{numBoxes}));
        clSetKernelArg(kernel, arg++, Sizeof.cl_float, Pointer.to(new float[]{binTemplate.w}));
        clSetKernelArg(kernel, arg++, Sizeof.cl_float, Pointer.to(new float[]{binTemplate.h}));
        clSetKernelArg(kernel, arg++, Sizeof.cl_float, Pointer.to(new float[]{binTemplate.d}));
        
        // 5. Execute kernel (one work item per ordering)
        long[] globalWorkSize = new long[]{numOrders};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, 
                              null, 0, null, null);
        
        // 6. Read results
        float[] scores = new float[numOrders];
        clEnqueueReadBuffer(queue, scoresMem, CL_TRUE, 0,
            Sizeof.cl_float * numOrders, Pointer.to(scores), 0, null, null);
        
        // 7. Clean up buffers
        clReleaseMemObject(boxesMem);
        clReleaseMemObject(ordersMem);
        clReleaseMemObject(scoresMem);
        
        // 8. Convert to List<Double>
        List<Double> result = new ArrayList<>();
        for (float score : scores) {
            result.add((double) score);
        }
        
        return result;
    }
    
    @Override
    public void release() {
        if (kernel != null) clReleaseKernel(kernel);
        if (program != null) clReleaseProgram(program);
        if (queue != null) clReleaseCommandQueue(queue);
        if (context != null) clReleaseContext(context);
    }
    
    private String loadKernelSource(String filename) {
        // Implementation to load kernel from resources
        // See KernelUtils.loadKernelSource() for reference
        return "...";
    }
}
```

### OpenCL Kernel Structure

Your OpenCL kernel could follow this pattern:

```c
__kernel void my_kernel_function(
    __global const float* boxes,     // Box dimensions [x, y, z, x, y, z, ...]
    __global const int* orders,      // Orderings [order0_idx0, order0_idx1, ...]
    __global float* scores,          // Output scores (one per ordering)
    const int num_boxes,
    const float bin_w,
    const float bin_h,
    const float bin_d
) {
    int order_id = get_global_id(0);  // Which ordering to evaluate
    
    // Your packing algorithm here
    // Process boxes in the order specified by orders[order_id * num_boxes ...]
    
    // Calculate packing density/score
    float score = calculate_score();
    
    scores[order_id] = score;
}
```

### Tips

- **Performance**: Minimize memory transfers between CPU and GPU
- **Kernel Templates**: Use templates with placeholders like `{{MAX_BINS}}` that get replaced at runtime
- **Reference Implementation**: Provide a CPU reference solver (`ReferenceSolver`) to reconstruct full solutions from winning orderings
- **Error Handling**: Check OpenCL return codes and kernel build logs
- **Memory Layout**: Use flat arrays for data transfer; avoid nested structures
- **Work Items**: Typically one work item per ordering for simplicity
- **Score Calculation**: Higher scores should indicate better packing (e.g., volume utilization percentage)

### Integration with GPUOptimizer

The `GPUOptimizer` class uses parallel solvers in genetic algorithms:
1. Generate population of orderings
2. Evaluate all orderings in parallel using `solve()`
3. Select best orderings based on scores
4. Create new generation through crossover/mutation
5. Repeat

This allows rapid exploration of the solution space using GPU acceleration.
