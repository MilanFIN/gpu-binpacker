package com.binpacker.lib.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.ocl.JOCLHelper;
import com.binpacker.lib.ocl.OpenCLDevice;
import com.binpacker.lib.solver.BestFitBSPOCL;
import com.binpacker.lib.solver.common.SolverProperties;

class BestFitBSPOCLTest {

	@Test
	void testSolve() {
		// Mock device settings
		OpenCLDevice device = new OpenCLDevice(0, 0, "testdevice");

		// Check if OpenCL is available
		if (JOCLHelper.getAvailableDevices().size() == 0) {
			System.out.println("Skipping test: No OpenCL devices found.");
			return;
		}

		BestFitBSPOCL solver = new BestFitBSPOCL();
		List<Box> boxes = new ArrayList<>();
		// Box 1 fits without rotation
		boxes.add(new Box(1, new Point3f(0, 0, 0), new Point3f(2, 2, 2)));
		// Box 2 fits without rotation
		boxes.add(new Box(2, new Point3f(0, 0, 0), new Point3f(3, 3, 3)));
		Bin binTemplate = new Bin(0, 10, 10, 10);

		SolverProperties properties = new SolverProperties(binTemplate, false, "x", List.of(0, 1, 2), device);
		solver.init(properties);

		try {
			List<List<Box>> result = solver.solve(boxes);

			assertEquals(1, result.size(), "Should fit in 1 bin");
			assertEquals(2, result.get(0).size(), "Both boxes should be packed");

			Box b1 = result.get(0).get(0);
			Box b2 = result.get(0).get(1);
			assertFalse(b1.collidesWith(b2), "Boxes should not intersect");

		} finally {
			solver.release();
		}
	}

	@Test
	void testRotation() {
		// Mock device settings
		OpenCLDevice device = new OpenCLDevice(0, 0, "testdevice");

		// Check if OpenCL is available
		if (JOCLHelper.getAvailableDevices().size() == 0) {
			return;
		}

		BestFitBSPOCL solver = new BestFitBSPOCL();
		List<Box> boxes = new ArrayList<>();

		// Create a bin with size 5 x 2 x 5
		Bin binTemplate = new Bin(0, 5, 2, 5);

		// Box 1: 5 x 2 x 2. Fits perfectly as is.
		boxes.add(new Box(1, new Point3f(0, 0, 0), new Point3f(5, 2, 2)));

		// Box 2: 2 x 5 x 2. Needs rotation to fit in 5x2x5 (if space remaining allows)
		// Wait, after Box 1 (5x2x2) is placed in 5x2x5:
		// Spaces remaining:
		// Right: 0 x 2 x 5 (Empty)
		// Top: 5 x 0 x 5 (Empty)
		// Front: 5 x 2 x 3 (Valid space at z=2)
		// So we have a 5x2x3 space at z=2.
		// Box 2 is 2x5x2.
		// Can it fit in 5x2x3?
		// Rotations:
		// 2x5x2 -> h=5 > space.h=2. Fail.
		// 5x2x2 -> w=5 <= 5, h=2 <= 2, d=2 <= 3. Fits!
		// So it should rotate and fit.

		boxes.add(new Box(2, new Point3f(0, 0, 0), new Point3f(2, 5, 2)));

		SolverProperties properties = new SolverProperties(binTemplate, false, "x", List.of(0, 1, 2), device);
		solver.init(properties);

		try {
			List<List<Box>> result = solver.solve(boxes);

			assertEquals(1, result.size(), "Should fit in 1 bin");
			assertEquals(2, result.get(0).size(), "Both boxes should be packed");

			Box b2Placed = result.get(0).get(1);
			// Verify it was rotated. Original size 2,5,2.
			// To fit in 5x2x3, it must be rotated so 'y' (height) becomes <= 2.
			assertTrue(b2Placed.size.y <= 2.01f, "Box 2 should be rotated to fit height 2");

		} finally {
			solver.release();
		}
	}
}
