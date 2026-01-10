package com.binpacker.lib.ocl;

import java.io.IOException;
import java.io.InputStream;

public class KernelUtils {

	public static String loadKernelSource(String resourcePath) {
		try (InputStream is = KernelUtils.class.getClassLoader().getResourceAsStream("kernels/" + resourcePath)) {
			if (is == null) {
				throw new RuntimeException("Kernel file not found: " + resourcePath);
			}
			return new String(is.readAllBytes());
		} catch (IOException e) {
			throw new RuntimeException("Error loading kernel source file", e);
		}
	}

}
