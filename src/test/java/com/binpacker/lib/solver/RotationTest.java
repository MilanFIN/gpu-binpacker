package com.binpacker.lib.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Space;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.solver.common.PlacementUtils;

public class RotationTest {

	@Test
	public void testRotationLogic() {
		// Space W=20, H=10, D=10
		Space space = new Space(0, 0, 0, 20, 10, 10);

		// Box 10x20x10
		// Needs Y (20) to align with W (20) to fit?
		// Or X(10) vs W(20) OK. Y(20) vs H(10) FAIL.
		// If we rotate so 20 is on W: Box Y (20) vs Space W (20). OK.
		// Box X (10) vs Space H (10). OK.
		// Box Z (10) vs Space D (10). OK.
		// This corresponds to Permutation 3 (y, x, z) or 4 (y, z, x).
		// These are group 1 (Y-axis).

		Box box = new Box(1, new Point3f(0, 0, 0), new Point3f(10, 20, 10));

		// 1. Test Null/Empty -> Should FAIL (only default checked, but box needs
		// Y-rotation)
		// Box 10x20x10. Space 20x10x10.
		// Default (x,y,z) = 10x20x10. 20 > 10 (Space H). Fail.
		assertNull(PlacementUtils.findFit(box, space, null), "Should fail with null rotations (default only)");
		assertNull(PlacementUtils.findFit(box, space, new ArrayList<>()),
				"Should fail with empty rotations (default only)");

		// 2. Test Axis 0 (X) -> Rotation around X: (x, z, y).
		// Box 10, 20, 10 -> 10, 10, 20.
		// Space 20, 10, 10.
		// X: 10<=20 OK. Y: 10<=10 OK. Z: 20<=10 FAIL.
		assertNull(PlacementUtils.findFit(box, space, Arrays.asList(0)), "Should fail with only X rotation");

		// 4. Test Axis 2 (Y) -> Rotation around Z: (y, x, z).
		// Box 10, 20, 10 -> 20, 10, 10.
		// Space 20, 10, 10.
		// X: 20<=20 OK. Y: 10<=10 OK. Z: 10<=10 OK. Fits.
		assertNotNull(PlacementUtils.findFit(box, space, Arrays.asList(1)), "Should fit with Y rotation");

		// 3. Test Axis 1 (Z) -> Rotation around Y: (z, y, x).
		// Box 10, 20, 10 -> 10, 20, 10.
		// Space 20, 10, 10.
		// Y: 20<=10 FAIL.
		// NOTE: User defined specific mappings. 1 -> zyx.
		assertNull(PlacementUtils.findFit(box, space, Arrays.asList(2)), "Should fail with only Z rotation");

		// Test multidimensional fitting
		// Space 10x20x10.
		// Box 20x10x10.
		// Default (20, 10, 10) vs (10, 20, 10). X 20 > 10. Fail.

		// X-Rot (x, z, y): 20, 10, 10. Fail.
		// Y-Rot (y, x, z): 10, 20, 10. X 10<=10. Y 20<=20. Z 10<=10. Fits.
		// Z-Rot (z, y, x): 10, 10, 20. X 10<=10. Y 10<=20. Z 20<=10 Fail.

		Space space2 = new Space(0, 0, 0, 10, 20, 10);
		Box box2 = new Box(2, new Point3f(0, 0, 0), new Point3f(20, 10, 10));

		assertNull(PlacementUtils.findFit(box2, space2, Arrays.asList(0)), "Should fail with only X rotation");
		assertNotNull(PlacementUtils.findFit(box2, space2, Arrays.asList(1)), "Should fit with Y rotation");
		assertNull(PlacementUtils.findFit(box2, space2, Arrays.asList(2)), "Should fail with only Z rotation");

	}
}
