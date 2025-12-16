package com.binpacker.lib.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

import com.binpacker.lib.ocl.KernelUtils;
import com.binpacker.lib.solver.common.Placement;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.solver.common.ocl.OCLCommon;
import com.binpacker.lib.solver.common.ocl.GPUBinState;

import org.jocl.*;
import static org.jocl.CL.*;

import com.binpacker.lib.ocl.OpenCLDevice;

public class BestFitBSPOCL implements SolverInterface {

	private String bestFitKernelSource;

	private Bin binTemplate;
	private boolean growingBin;
	private String growAxis;

	// OpenCL resources
	private OCLCommon ocl = new OCLCommon();
	private cl_kernel bestFitKernel;

	// Map to track GPU resources for each bin
	private Map<Bin, GPUBinState> binStates = new HashMap<>();

	@Override
	public void init(SolverProperties properties) {
		this.binTemplate = properties.bin;
		this.growingBin = properties.growingBin;
		this.growAxis = properties.growAxis;
		this.bestFitKernelSource = KernelUtils.loadKernelSource("bestfit_rotate.cl");

		initOpenCL(properties.openCLDevice);
	}

	private void initOpenCL(OpenCLDevice preference) {
		ocl.init(bestFitKernelSource, preference);
		bestFitKernel = clCreateKernel(ocl.clProgram, "bestfit_rotate", null);
	}

	@Override
	public List<List<Box>> solve(List<Box> boxes) {
		List<Bin> activeBins = new ArrayList<>();
		List<List<Box>> result = new ArrayList<>();

		if (growingBin) {
			switch (growAxis) {
				case "x":
					binTemplate.w = Integer.MAX_VALUE;
					break;
				case "y":
					binTemplate.h = Integer.MAX_VALUE;
					break;
				case "z":
					binTemplate.d = Integer.MAX_VALUE;
					break;
				default:
					System.err.println("Invalid growAxis specified: " + growAxis);
					binTemplate.h = Integer.MAX_VALUE;
					break;
			}
		}

		// Initialize first bin
		Bin firstBin = new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d);
		activeBins.add(firstBin);
		initBinState(firstBin);

		for (Box box : boxes) {
			boolean placed = false;

			// Evaluate all active bins to find the global best fit
			for (Bin bin : activeBins) {
				ExtendedPlacement fit = findFit(box, bin);
				if (fit != null) {
					placeBoxGPU(box, bin, fit.spaceIndex, fit.rotationIndex);
					placed = true;
					break;
				}
			}

			if (!placed) {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);
				initBinState(newBin);

				// For the new bin, try to fit
				ExtendedPlacement fit = findFit(box, newBin);
				if (fit != null) {
					placeBoxGPU(box, newBin, fit.spaceIndex, fit.rotationIndex);
				} else {
					System.err.println("Box too big for bin: " + box);
				}
			}
		}

		if (growingBin) {
			switch (growAxis) {
				case "x":
					float maxX = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxX = Math.max(maxX, placedBox.position.x + placedBox.size.x);
					}
					activeBins.get(0).w = maxX;
					break;
				case "y":
					float maxY = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxY = Math.max(maxY, placedBox.position.y + placedBox.size.y);
					}
					activeBins.get(0).h = maxY;
					break;
				case "z":
					float maxZ = 0;
					for (Box placedBox : activeBins.get(0).boxes) {
						maxZ = Math.max(maxZ, placedBox.position.z + placedBox.size.z);
					}
					activeBins.get(0).d = maxZ;
					break;
			}
		}

		for (Bin bin : activeBins) {
			result.add(bin.boxes);
		}

		return result;
	}

	private void initBinState(Bin bin) {
		GPUBinState state = new GPUBinState(ocl, 7, 6);
		binStates.put(bin, state);
		fullRewrite(bin); // Write initial spaces
	}

	// Writes all spaces of the bin to the GPU buffer
	private void fullRewrite(Bin bin) {
		GPUBinState state = binStates.get(bin);
		List<Space> spaces = bin.freeSpaces;
		int numSpaces = spaces.size();

		// Resize if needed
		if (numSpaces > state.capacity) {
			state.allocateBuffers(Math.max(state.capacity * 2, numSpaces));
		}

		float[] spaceData = new float[numSpaces * 7];
		for (int i = 0; i < numSpaces; i++) {
			Space s = spaces.get(i);
			spaceData[i * 7 + 0] = s.w; // Using w,h,d as x,y,z size, but wait. Kernel expects x,y,z,w,h,d,bin_idx
			// Space class has x, y, z, w, h, d
			spaceData[i * 7 + 0] = s.x;
			spaceData[i * 7 + 1] = s.y;
			spaceData[i * 7 + 2] = s.z;
			spaceData[i * 7 + 3] = s.w;
			spaceData[i * 7 + 4] = s.h;
			spaceData[i * 7 + 5] = s.d;
			spaceData[i * 7 + 6] = bin.index; // bin index or custom ID
		}

		// Write to GPU
		if (numSpaces > 0) {
			clEnqueueWriteBuffer(ocl.clQueue, state.inputBuffer, CL_TRUE, 0,
					(long) Sizeof.cl_float * spaceData.length, Pointer.to(spaceData), 0, null, null);
		}
		state.count = numSpaces;
	}

	private void updateSpaceOnGPU(GPUBinState state, int index, Space space, int binId) {
		float[] data = new float[] { space.x, space.y, space.z, space.w, space.h, space.d, binId };
		clEnqueueWriteBuffer(ocl.clQueue, state.inputBuffer, CL_TRUE,
				(long) Sizeof.cl_float * index * 7,
				(long) Sizeof.cl_float * 7,
				Pointer.to(data), 0, null, null);
	}

	private void placeBoxGPU(Box box, Bin bin, int spaceIndex, int rotationIndex) {
		GPUBinState state = binStates.get(bin);
		Space space = bin.freeSpaces.get(spaceIndex);

		// Apply rotation
		applyRotation(box, rotationIndex);

		// Add box to bin
		Box placedBox = new Box(box.id,
				new Point3f(space.x, space.y, space.z),
				new Point3f(box.size.x, box.size.y, box.size.z));
		bin.boxes.add(placedBox);

		// Calculate new spaces (BSP)
		// Right: Remaining width
		Space right = new Space(space.x + box.size.x, space.y, space.z,
				space.w - box.size.x, space.h, space.d);

		// Top: Remaining height above box (width limited to box width)
		Space top = new Space(space.x, space.y + box.size.y, space.z,
				box.size.x, space.h - box.size.y, space.d);

		// Front: Remaining depth in front of box (width/height limited to box w/h)
		Space front = new Space(space.x, space.y, space.z + box.size.z,
				box.size.x, box.size.y, space.d - box.size.z);

		// Remove the 'used' space using swap-remove
		int lastIdx = bin.freeSpaces.size() - 1;
		if (spaceIndex != lastIdx) {
			Space lastSpace = bin.freeSpaces.get(lastIdx);
			bin.freeSpaces.set(spaceIndex, lastSpace);
			updateSpaceOnGPU(state, spaceIndex, lastSpace, bin.index);
		}
		bin.freeSpaces.remove(lastIdx);
		state.count--;

		// Add new valid spaces
		addSpaceIfValid(bin, state, right);
		addSpaceIfValid(bin, state, top);
		addSpaceIfValid(bin, state, front);
	}

	private void applyRotation(Box box, int rotation) {
		if (rotation == 0)
			return; // (x, y, z)

		Point3f s = box.size;
		float ox = s.x, oy = s.y, oz = s.z;

		if (rotation == 1) { // xzy
			s.x = ox;
			s.y = oz;
			s.z = oy;
		} else if (rotation == 2) { // yxz
			s.x = oy;
			s.y = ox;
			s.z = oz;
		} else if (rotation == 3) { // yzx
			s.x = oy;
			s.y = oz;
			s.z = ox;
		} else if (rotation == 4) { // zxy
			s.x = oz;
			s.y = ox;
			s.z = oy;
		} else if (rotation == 5) { // zyx
			s.x = oz;
			s.y = oy;
			s.z = ox;
		}
	}

	// Extended Placement class to hold rotation
	// Placement has box, spaceIndex. We can hack it or just use a custom structure
	// internally?
	// The problem is PlacementUtils or whatever consumes it might expect standard
	// fields.
	// Placement class: public Box box; public int spaceIndex;
	// I'll extend it locally or add a field if I can (I can't without modifying
	// common code).
	// But `Box` object in Placement is usually the *placed* box?
	// No, checking Placement.java: Box box, int spaceIndex.
	// `solve` uses existing Placement class.
	// The `findFit` returns a Placement.
	// I will subclass it locally or just add a field if I can.
	// Wait, I can't modify Placement easily if it's in another file without a
	// separate tool call.
	// I will just make `findFit` return a custom object, and convert to Placement
	// or just Use the index.
	// Actually, `findFit` is private. I can change its signature or return type.

	private static class ExtendedPlacement extends Placement {
		int rotationIndex;
		float score;

		public ExtendedPlacement(Box box, int spaceIndex, int rotationIndex, float score) {
			super(box, spaceIndex);
			this.rotationIndex = rotationIndex;
			this.score = score;
		}
	}

	private void addSpaceIfValid(Bin bin, GPUBinState state, Space space) {
		if (space.w > 0 && space.h > 0 && space.d > 0) {
			bin.freeSpaces.add(space);
			int index = bin.freeSpaces.size() - 1;

			// Resize check
			if (bin.freeSpaces.size() > state.capacity) {
				fullRewrite(bin);
			} else {
				// Append to GPU
				updateSpaceOnGPU(state, index, space, bin.index);
				state.count++;
			}
		}
	}

	public void release() {
		for (GPUBinState state : binStates.values()) {
			state.release();
		}
		binStates.clear();

		clReleaseKernel(bestFitKernel);
		ocl.release();
	}

	private ExtendedPlacement findFit(Box box, Bin bin) {
		List<Space> spaces = bin.freeSpaces;
		if (spaces.isEmpty())
			return null;

		GPUBinState state = binStates.get(bin);
		int numSpaces = spaces.size();

		// Set args
		clSetKernelArg(bestFitKernel, 0, Sizeof.cl_float, Pointer.to(new float[] { box.size.x }));
		clSetKernelArg(bestFitKernel, 1, Sizeof.cl_float, Pointer.to(new float[] { box.size.y }));
		clSetKernelArg(bestFitKernel, 2, Sizeof.cl_float, Pointer.to(new float[] { box.size.z }));
		clSetKernelArg(bestFitKernel, 3, Sizeof.cl_mem, Pointer.to(state.inputBuffer));
		clSetKernelArg(bestFitKernel, 4, Sizeof.cl_mem, Pointer.to(state.outputBuffer));

		// Run
		long[] globalWorkSize = new long[] { numSpaces };
		clEnqueueNDRangeKernel(ocl.clQueue, bestFitKernel, 1, null, globalWorkSize, null, 0, null, null);

		// Read back results
		// 6 floats per space
		float[] results = new float[numSpaces * 6];
		clEnqueueReadBuffer(ocl.clQueue, state.outputBuffer, CL_TRUE, 0,
				(long) Sizeof.cl_float * numSpaces * 6, Pointer.to(results), 0, null, null);

		// Find best fit (lowest non-zero score)
		int bestSpaceIndex = -1;
		int bestRotationIndex = -1;
		float minScore = Float.MAX_VALUE;

		for (int i = 0; i < numSpaces; i++) {
			for (int r = 0; r < 6; r++) {
				float score = results[i * 6 + r];
				if (score > 0.0f && score < minScore) {
					minScore = score;
					bestSpaceIndex = i;
					bestRotationIndex = r;
				}
			}
		}

		if (bestSpaceIndex != -1) {
			return new ExtendedPlacement(box, bestSpaceIndex, bestRotationIndex, minScore);
		}

		return null;
	}

}
