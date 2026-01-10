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
import com.binpacker.lib.solver.FFBSPOCL;
import com.binpacker.lib.solver.common.SolverProperties;

class FFBSPOCLTest {

	@Test
	void testSolve() {

		// replace with the actual opencl device's
		// * platform index
		// * device index
		// * name (not used)
		OpenCLDevice device = new OpenCLDevice(0, 0, "testdevice");

		// OpenCL is probably not available in github actions, so skipping
		// if no devices are found.
		if (JOCLHelper.getAvailableDevices().size() == 0) {
			return;
		}

		FFBSPOCL solver = new FFBSPOCL();
		List<Box> boxes = new ArrayList<>();
		boxes.add(new Box(1, new Point3f(0, 0, 0), new Point3f(2, 2, 2)));
		boxes.add(new Box(2, new Point3f(0, 0, 0), new Point3f(3, 3, 3)));
		Bin binTemplate = new Bin(0, 10, 10, 10);

		SolverProperties properties = new SolverProperties(binTemplate, false, "x", device);
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
