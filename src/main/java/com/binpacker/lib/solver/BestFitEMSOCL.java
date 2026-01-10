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

public class BestFitEMSOCL implements SolverInterface {

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

			// Iterate active bins to find best fit
			Bin bestBin = null;
			ExtendedPlacement bestPlacement = null;

			// Standard strategy: Fit in first available bin?
			// Or Best Fit Global? BestFit implies Global usually, but BestFitEMS usually
			// iterates bins.
			// Let's iterate all bins and find the absolute best score.

			for (Bin bin : activeBins) {
				ExtendedPlacement fit = findFit(box, bin);
				if (fit != null) {
					if (bestPlacement == null || fit.score < bestPlacement.score) {
						bestPlacement = fit;
						bestBin = bin;
					}
				}
			}

			if (bestPlacement != null) {
				placeBox(box, bestBin, bestPlacement.spaceIndex, bestPlacement.rotationIndex);
				placed = true;
			} else {
				// Create new bin
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);
				initBinState(newBin);

				// Try fit in new bin
				ExtendedPlacement fit = findFit(box, newBin);
				if (fit != null) {
					placeBox(box, newBin, fit.spaceIndex, fit.rotationIndex);
				} else {
					System.err.println("Box too big for bin: " + box);
				}
			}
		}

		// Adjust growing bin size
		if (growingBin) {
			// ... logic same as other solvers
			switch (growAxis) {
				case "x":
					float maxX = 0;
					for (Box placedBox : activeBins.get(0).boxes)
						maxX = Math.max(maxX, placedBox.position.x + placedBox.size.x);
					activeBins.get(0).w = maxX;
					break;
				case "y":
					float maxY = 0;
					for (Box placedBox : activeBins.get(0).boxes)
						maxY = Math.max(maxY, placedBox.position.y + placedBox.size.y);
					activeBins.get(0).h = maxY;
					break;
				case "z":
					float maxZ = 0;
					for (Box placedBox : activeBins.get(0).boxes)
						maxZ = Math.max(maxZ, placedBox.position.z + placedBox.size.z);
					activeBins.get(0).d = maxZ;
					break;
			}
		}

		for (Bin bin : activeBins) {
			result.add(bin.boxes);
		}

		return result;
	}

	private void placeBox(Box box, Bin bin, int spaceIndex, int rotationIndex) {
		GPUBinState state = binStates.get(bin);
		// Get the space we are placing into (just for coordinate ref, it will be
		// removed by pruning loop)
		Space targetSpace = bin.freeSpaces.get(spaceIndex);

		// Apply rotation
		Box placedBox = new Box(box.id, new Point3f(targetSpace.x, targetSpace.y, targetSpace.z),
				new Point3f(box.size.x, box.size.y, box.size.z));
		applyRotation(placedBox, rotationIndex);

		bin.boxes.add(placedBox);

		// Prune colliding spaces
		// We must check ALL spaces, because EMS spaces overlap.
		for (int i = bin.freeSpaces.size() - 1; i >= 0; i--) {
			Space s = bin.freeSpaces.get(i);
			if (placedBox.collidesWith(s)) {
				// Remove space 's' (at index i)
				removeSpaceGPU(bin, state, i);

				// Add new split spaces
				splitCollidingFreeSpaceGPU(placedBox, s, bin, state);
			}
		}

		// Prune wrapped spaces
		bin.utilCounter++;
		if (bin.utilCounter > 10) {
			pruneWrappedSpacesGPU(bin, state);
			bin.utilCounter = 0;
		}
	}

	private void removeSpaceGPU(Bin bin, GPUBinState state, int index) {
		int lastIdx = bin.freeSpaces.size() - 1;
		if (index != lastIdx) {
			Space lastSpace = bin.freeSpaces.get(lastIdx);
			bin.freeSpaces.set(index, lastSpace);
			// Update GPU at 'index' with 'lastSpace'
			updateSpaceOnGPU(state, index, lastSpace, bin.index);
		}
		bin.freeSpaces.remove(lastIdx);
		state.count--;
	}

	private void addSpaceGPU(Bin bin, GPUBinState state, Space space) {
		bin.freeSpaces.add(space);
		int index = bin.freeSpaces.size() - 1;

		if (bin.freeSpaces.size() > state.capacity) {
			fullRewrite(bin); // Expand capacity
		} else {
			updateSpaceOnGPU(state, index, space, bin.index);
			state.count++;
		}
	}

	private void splitCollidingFreeSpaceGPU(Box box, Space space, Bin bin, GPUBinState state) {
		// 1. Right
		if (box.position.x + box.size.x < space.x + space.w) {
			Space right = new Space(box.position.x + box.size.x, space.y, space.z,
					(space.x + space.w) - (box.position.x + box.size.x), space.h, space.d);
			addSpaceGPU(bin, state, right);
		}
		// 2. Left
		if (box.position.x > space.x) {
			Space left = new Space(space.x, space.y, space.z,
					box.position.x - space.x, space.h, space.d);
			addSpaceGPU(bin, state, left);
		}
		// 3. Top
		if (box.position.y + box.size.y < space.y + space.h) {
			Space top = new Space(space.x, box.position.y + box.size.y, space.z,
					space.w, (space.y + space.h) - (box.position.y + box.size.y), space.d);
			addSpaceGPU(bin, state, top);
		}
		// 4. Bottom
		if (box.position.y > space.y) {
			Space bottom = new Space(space.x, space.y, space.z,
					space.w, box.position.y - space.y, space.d);
			addSpaceGPU(bin, state, bottom);
		}
		// 5. Front
		if (box.position.z + box.size.z < space.z + space.d) {
			Space front = new Space(space.x, space.y, box.position.z + box.size.z,
					space.w, space.h, (space.z + space.d) - (box.position.z + box.size.z));
			addSpaceGPU(bin, state, front);
		}
		// 6. Back
		if (box.position.z > space.z) {
			Space back = new Space(space.x, space.y, space.z,
					space.w, space.h, box.position.z - space.z);
			addSpaceGPU(bin, state, back);
		}
	}

	private void pruneWrappedSpacesGPU(Bin bin, GPUBinState state) {
		for (int i = bin.freeSpaces.size() - 1; i >= 0; i--) {
			Space s1 = bin.freeSpaces.get(i);
			if (s1.w <= 0 || s1.h <= 0 || s1.d <= 0) {
				removeSpaceGPU(bin, state, i);
				continue;
			}

			boolean isWrapped = false;
			for (int j = bin.freeSpaces.size() - 1; j >= 0; j--) {
				if (i == j)
					continue;
				Space s2 = bin.freeSpaces.get(j);
				if (s1.x >= s2.x && s1.y >= s2.y && s1.z >= s2.z &&
						(s1.x + s1.w) <= (s2.x + s2.w) &&
						(s1.y + s1.h) <= (s2.y + s2.h) &&
						(s1.z + s1.d) <= (s2.z + s2.d)) {
					isWrapped = true;
					break;
				}
			}
			if (isWrapped) {
				removeSpaceGPU(bin, state, i);
			}
		}
	}

	// --- Boilerplate & Helpers (same as BestFitBSPOCL mostly) ---

	private void initBinState(Bin bin) {
		GPUBinState state = new GPUBinState(ocl, 7, 6);
		binStates.put(bin, state);
		fullRewrite(bin);
	}

	private void fullRewrite(Bin bin) {
		GPUBinState state = binStates.get(bin);
		List<Space> spaces = bin.freeSpaces;
		int numSpaces = spaces.size();

		if (numSpaces > state.capacity) {
			state.allocateBuffers(Math.max(state.capacity * 2, numSpaces));
		}

		float[] spaceData = new float[numSpaces * 7];
		for (int i = 0; i < numSpaces; i++) {
			Space s = spaces.get(i);
			spaceData[i * 7 + 0] = s.x;
			spaceData[i * 7 + 1] = s.y;
			spaceData[i * 7 + 2] = s.z;
			spaceData[i * 7 + 3] = s.w;
			spaceData[i * 7 + 4] = s.h;
			spaceData[i * 7 + 5] = s.d;
			spaceData[i * 7 + 6] = bin.index;
		}

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

	private ExtendedPlacement findFit(Box box, Bin bin) {
		List<Space> spaces = bin.freeSpaces;
		if (spaces.isEmpty())
			return null;

		GPUBinState state = binStates.get(bin);
		int numSpaces = spaces.size();

		// Set args matches kernel signature
		clSetKernelArg(bestFitKernel, 0, Sizeof.cl_float, Pointer.to(new float[] { box.size.x }));
		clSetKernelArg(bestFitKernel, 1, Sizeof.cl_float, Pointer.to(new float[] { box.size.y }));
		clSetKernelArg(bestFitKernel, 2, Sizeof.cl_float, Pointer.to(new float[] { box.size.z }));
		clSetKernelArg(bestFitKernel, 3, Sizeof.cl_mem, Pointer.to(state.inputBuffer));
		clSetKernelArg(bestFitKernel, 4, Sizeof.cl_mem, Pointer.to(state.outputBuffer));

		long[] globalWorkSize = new long[] { numSpaces };
		clEnqueueNDRangeKernel(ocl.clQueue, bestFitKernel, 1, null, globalWorkSize, null, 0, null, null);

		float[] results = new float[numSpaces * 6];
		clEnqueueReadBuffer(ocl.clQueue, state.outputBuffer, CL_TRUE, 0,
				(long) Sizeof.cl_float * numSpaces * 6, Pointer.to(results), 0, null, null);

		// Find best fit (lowest non-zero score for 'best fit')
		// Kernel returns 'score' which is size based. Smaller score = better fit?
		// Wait, kernel returns `x + y + z + 1.0f`.
		// EMS scoring usually prefers smaller distance from origin (bottom-left-back).
		// So yes, smaller score is better.

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

	private void applyRotation(Box box, int rotation) {
		if (rotation == 0)
			return;
		Point3f s = box.size;
		float ox = s.x, oy = s.y, oz = s.z;
		if (rotation == 1) {
			s.x = ox;
			s.y = oz;
			s.z = oy;
		} else if (rotation == 2) {
			s.x = oy;
			s.y = ox;
			s.z = oz;
		} else if (rotation == 3) {
			s.x = oy;
			s.y = oz;
			s.z = ox;
		} else if (rotation == 4) {
			s.x = oz;
			s.y = ox;
			s.z = oy;
		} else if (rotation == 5) {
			s.x = oz;
			s.y = oy;
			s.z = ox;
		}
	}

	private static class ExtendedPlacement extends Placement {
		int rotationIndex;
		float score;

		public ExtendedPlacement(Box box, int spaceIndex, int rotationIndex, float score) {
			super(box, spaceIndex);
			this.rotationIndex = rotationIndex;
			this.score = score;
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
}
