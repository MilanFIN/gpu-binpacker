# CSV Input/Output Structure

This document describes the CSV file formats used for input (box dimensions) and output (packing solution).

## Input CSV Format

The input CSV file defines the boxes to be packed. Each line represents one box with three dimensions and an optional weight.

### Structure

```
width,height,depth
width,height,depth,weight
```

### Fields

- **width** (float): Width of the box (X dimension)
- **height** (float): Height of the box (Y dimension)  
- **depth** (float): Depth of the box (Z dimension)
- **weight** (float, optional): Weight of the box (defaults to 0 if not specified)

### Example

```csv
7,6,7,5
9,7,6,5
8,6,4,5
8,6,8,5
10,9,8,5
```

**Note**: The weight column (4th field) is optional. CSVs with only 3 columns (width, height, depth) are still supported for backward compatibility.

### Rules

- Each line must contain at least 3 comma-separated numeric values
- The 4th column (weight) is optional and defaults to 0 if not provided
- Empty lines and lines starting with `#` are ignored
- Leading/trailing whitespace is trimmed
- Boxes are assigned sequential IDs starting from 0

### Loading Input CSV

The application loads input CSV files using `GuiApp.loadBoxesFromCsv()`:

```java
private List<Box> loadBoxesFromCsv(File file) {
    List<Box> boxes = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
        String line;
        int idCounter = 0;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue; // Skip empty or comment lines
            
            String[] parts = line.split(",");
            if (parts.length >= 3) {
                try {
                    float w = Float.parseFloat(parts[0].trim());
                    float h = Float.parseFloat(parts[1].trim());
                    float d = Float.parseFloat(parts[2].trim());
                    float weight = 0; // default weight
                    if (parts.length >= 4) {
                        weight = Float.parseFloat(parts[3].trim());
                    }
                    
                    Box box = new Box(
                        new Point3f(0, 0, 0),
                        new Point3f(w, h, d));
                    box.id = idCounter++;
                    box.weight = weight;
                    boxes.add(box);
                } catch (NumberFormatException e) {
                    System.err.println("Skipping invalid line: " + line);
                }
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
    return boxes;
}
```

## Output CSV Format

The output CSV file represents the packing solution, showing the position and dimensions of each box in each bin.

### Structure

```
Bin,Box,x,y,z,w,h,d
```

### Fields

- **Bin** (int): Bin index (0-based)
- **Box** (int): Box id
- **x** (float): X position of the box in the bin
- **y** (float): Y position of the box in the bin
- **z** (float): Z position of the box in the bin
- **w** (float): Width of the box
- **h** (float): Height of the box
- **d** (float): Depth of the box

### Example

```csv
Bin,Box,x,y,z,w,h,d
0,3,0.0,0.0,0.0,7.0,6.0,7.0
0,1,7.0,0.0,0.0,9.0,7.0,6.0
0,2,0.0,6.0,0.0,8.0,6.0,4.0
1,4,0.0,0.0,0.0,8.0,6.0,8.0
1,5,8.0,0.0,0.0,10.0,9.0,8.0
```

### Exporting Output CSV

The application exports solutions using `Utils.exportCsv()`:

```java
static String exportCsv(List<List<Box>> bins) {
    String csv = "Bin,Box,x,y,z,w,h,d\n";
    for (int i = 0; i < bins.size(); i++) {
        List<Box> bin = bins.get(i);
        for (int j = 0; j < bin.size(); j++) {
            Box box = bin.get(j);
            csv += i + "," + j + "," + box.position.x + "," + box.position.y + "," + box.position.z + ","
                    + box.size.x + "," + box.size.y + "," + box.size.z + "\n";
        }
    }
    return csv;
}
```

## Notes

- Position coordinates represent the bottom-left-front corner of each box
- Dimensions include any rotations applied by the solver
- Multiple bins may be used if boxes don't fit in a single bin
- When using "growing bin" mode, only one bin is used with an expandable dimension
