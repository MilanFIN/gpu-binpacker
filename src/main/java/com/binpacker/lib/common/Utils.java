package com.binpacker.lib.common;

import java.util.List;

public interface Utils {
	static String exportCsv(List<List<Box>> bins) {
		String csv = "Bin,Box,x, y, z, w ,h ,d \n";
		for (int i = 0; i < bins.size(); i++) {
			List<Box> bin = bins.get(i);
			for (int j = 0; j < bin.size(); j++) {
				Box box = bin.get(j);
				csv += i + "," + box.id + "," + box.position.x + "," + box.position.y + "," + box.position.z + ","
						+ box.size.x + "," + box.size.y + "," + box.size.z + "\n";
			}
		}
		return csv;
	}
}
