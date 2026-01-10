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
import com.binpacker.lib.solver.BestFitEMSOCL;
import com.binpacker.lib.solver.common.SolverProperties;

class BestFitEMSOCLTest {

	@Test
	void testSolve() {
		// Mock device settings
		OpenCLDevice device = new OpenCLDevice(0, 0, "testdevice");

		// Check if OpenCL is available
		if (JOCLHelper.getAvailableDevices().size() == 0) {
			System.out.println("Skipping test: No OpenCL devices found.");
			return;
		}

		BestFitEMSOCL solver = new BestFitEMSOCL();
		List<Box> boxes = new ArrayList<>();
		// Box 1
		boxes.add(new Box(1, new Point3f(0, 0, 0), new Point3f(2, 2, 2)));
		// Box 2
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

}
