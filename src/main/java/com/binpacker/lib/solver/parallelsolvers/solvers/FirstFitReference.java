package com.binpacker.lib.solver.parallelsolvers.solvers;

import java.util.ArrayList;
import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Space;

public class FirstFitReference {

	public List<Bin> solve(List<Box> boxes, List<Integer> order, Bin binTemplate) {
		List<Bin> activeBins = new ArrayList<>();

		// Initialize first bin
		activeBins.add(new Bin(0, binTemplate.w, binTemplate.h, binTemplate.d));

		// Iterate through boxes in the given order
		for (int boxIndex : order) {
			Box originalBox = boxes.get(boxIndex);
			// Create a copy of the box to store placement
			Box box = new Box(originalBox.id, new Point3f(0, 0, 0),
					new Point3f(originalBox.size.x, originalBox.size.y, originalBox.size.z));

			boolean placed = false;

			// Try to fit in existing bins
			for (Bin bin : activeBins) {
				List<Space> spaces = bin.freeSpaces;

				// Iterate spaces in bin
				for (int s = 0; s < spaces.size(); s++) {
					Space sp = spaces.get(s);

					if (box.size.x <= sp.w && box.size.y <= sp.h && box.size.z <= sp.d) {
						// Fit found!
						placed = true;

						// Set position
						box.position.x = sp.x;
						box.position.y = sp.y;
						box.position.z = sp.z;
						bin.boxes.add(box);

						// Remove used space (swap with last for efficiency, same as kernel)
						int lastIdx = spaces.size() - 1;
						if (s != lastIdx) {
							spaces.set(s, spaces.get(lastIdx));
						}
						spaces.remove(lastIdx);
						// Note: if we swapped, we must re-evaluate index s if we were continuing,
						// but here we break immediately after placement, so it's fine.
						// Wait, the kernel breaks the SPACE loop, next it continues to next BOX.
						// Since we break the BIN loop too, we are done with this box.

						// Create new spaces (Guillotine Split)
						// Right
						if (sp.w - box.size.x > 0) {
							spaces.add(new Space(
									sp.x + box.size.x, sp.y, sp.z,
									sp.w - box.size.x, sp.h, sp.d));
						}
						// Top
						if (sp.h - box.size.y > 0) {
							spaces.add(new Space(
									sp.x, sp.y + box.size.y, sp.z,
									box.size.x, sp.h - box.size.y, sp.d));
						}
						// Front
						if (sp.d - box.size.z > 0) {
							spaces.add(new Space(
									sp.x, sp.y, sp.z + box.size.z,
									box.size.x, box.size.y, sp.d - box.size.z));
						}

						break; // Break space loop
					}
				}

				if (placed)
					break; // Break bin loop
			}

			// If not placed, create new bin
			if (!placed) {
				Bin newBin = new Bin(activeBins.size(), binTemplate.w, binTemplate.h, binTemplate.d);
				activeBins.add(newBin);

				// We know it fits in empty bin (assuming box <= bin dimensions)
				// But to be consistent with kernel logic, we add the initial space and then
				// "find" it.
				// However, we can just place it directly to save a step if we trust logic,
				// but let's strictly follow the "try to fit" pattern or just manual placement
				// logic
				// to ensure exact same split behavior.

				Space sp = newBin.freeSpaces.get(0); // The single initial space

				if (box.size.x <= sp.w && box.size.y <= sp.h && box.size.z <= sp.d) {
					box.position.x = sp.x;
					box.position.y = sp.y;
					box.position.z = sp.z;
					newBin.boxes.add(box);

					newBin.freeSpaces.remove(0);

					// Right
					if (sp.w - box.size.x > 0) {
						newBin.freeSpaces.add(new Space(
								sp.x + box.size.x, sp.y, sp.z,
								sp.w - box.size.x, sp.h, sp.d));
					}
					// Top
					if (sp.h - box.size.y > 0) {
						newBin.freeSpaces.add(new Space(
								sp.x, sp.y + box.size.y, sp.z,
								box.size.x, sp.h - box.size.y, sp.d));
					}
					// Front
					if (sp.d - box.size.z > 0) {
						newBin.freeSpaces.add(new Space(
								sp.x, sp.y, sp.z + box.size.z,
								box.size.x, box.size.y, sp.d - box.size.z));
					}
				} else {
					// Should not happen if box fits in bin template
					System.err.println("Box " + box.id + " too large for bin template!");
				}
			}
		}

		return activeBins;
	}

}
