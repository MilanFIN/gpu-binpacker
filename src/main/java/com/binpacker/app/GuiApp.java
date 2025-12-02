package com.binpacker.app;

import com.binpacker.lib.common.Point3f;
import com.binpacker.lib.common.Utils;
import com.binpacker.lib.optimizer.GAOptimizer;
import com.binpacker.lib.optimizer.Optimizer;
import com.binpacker.lib.solver.BestFit3D;
import com.binpacker.lib.solver.FirstFit2D;
import com.binpacker.lib.solver.FirstFit3D;
import com.binpacker.lib.solver.MOAB;
import com.binpacker.lib.solver.Solver;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import javafx.scene.shape.Box;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.DrawMode;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.io.File;
import javafx.stage.FileChooser;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class NumberTextField extends TextField {

	private float value;

	public NumberTextField(float value) {
		this.value = value;
		this.setText(String.valueOf(value));

		this.textProperty().addListener((observable, oldValue, newValue) -> {
			try {
				this.value = Float.parseFloat(newValue);
			} catch (NumberFormatException e) {
				// Handle cases where the text might be empty or not a valid number
				// (though replaceText/replaceSelection should prevent non-numeric input)
				this.value = 0.0f; // Default to 0.0 if parsing fails
			}
		});
	}

	@Override
	public void replaceText(int start, int end, String text) {
		if (text.matches("[0-9]*")) {
			super.replaceText(start, end, text);
		}
	}

	@Override
	public void replaceSelection(String text) {
		if (text.matches("[0-9]*")) {
			super.replaceSelection(text);
		}
	}

	public float getValue() {
		return value;
	}
}

public class GuiApp extends Application {

	private Group world;
	private Label statusLabel;
	private Stage primaryStage;

	// Camera controls
	private double lastMouseX;
	private double lastMouseY;
	private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
	private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);

	private List<List<com.binpacker.lib.common.Box>> result;

	private ComboBox<Solver> solverComboBox;

	private int generations = 200;
	private int population = 30;
	private int eliteCount = 3;
	private boolean growingBin = false;

	private String axis = "x";

	NumberTextField binWidthField = new NumberTextField(30);
	NumberTextField binHeightField = new NumberTextField(30);
	NumberTextField binDepthField = new NumberTextField(30);

	@Override
	public void start(Stage primaryStage) {
		StackPane root = new StackPane();

		// 3D Scene
		world = new Group();
		SubScene subScene = create3DScene(world);
		// Ensure SubScene resizes with the window
		subScene.heightProperty().bind(root.heightProperty());
		subScene.widthProperty().bind(root.widthProperty());

		root.getChildren().add(subScene);

		// Controls
		VBox controls = new VBox(16);

		Label binLabel = new Label("Bin dimensions:");

		HBox binDimensionFields = new HBox(1);
		binDimensionFields.setMaxWidth(200); // Example width, adjust as needed
		binDimensionFields.setAlignment(Pos.CENTER_LEFT);
		binDimensionFields.getChildren().addAll(binWidthField, binHeightField, binDepthField);

		controls.getChildren().addAll(binLabel, binDimensionFields);

		this.solverComboBox = new ComboBox<>();
		this.solverComboBox.setConverter(new javafx.util.StringConverter<Solver>() {
			@Override
			public String toString(Solver solver) {
				if (solver == null) {
					return null;
				}
				// Provide user-friendly names for each solver type
				if (solver instanceof FirstFit3D) {
					return "First Fit 3D";
				} else if (solver instanceof FirstFit2D) {
					return "First Fit 2D";
				} else if (solver instanceof BestFit3D) {
					return "Best Fit 3D";
				} else if (solver instanceof MOAB) {
					return "MOAB";
				}
				return solver.getClass().getSimpleName(); // Fallback
			}

			@Override
			public Solver fromString(String string) {
				// This method is used when parsing user input, not needed for simple selection
				return null;
			}
		});
		this.solverComboBox.getItems().addAll(new FirstFit3D(), new FirstFit2D(), new BestFit3D(), new MOAB());
		this.solverComboBox.setValue(this.solverComboBox.getItems().get(0)); // Set default to the first item

		Button solveButton = new Button("Solve");
		Button exportButton = new Button("Export currently visible solution");

		Label generationsLabel = new Label("Generations:");
		javafx.scene.control.TextField generationsField = new javafx.scene.control.TextField(
				String.valueOf(generations));
		generationsField.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*")) {
				generationsField.setText(oldValue);
			} else {
				try {
					generations = Integer.parseInt(newValue);
				} catch (NumberFormatException ex) {
					generations = 0; // Or some default/error handling
				}
			}
		});

		Label populationLabel = new Label("Population:");
		javafx.scene.control.TextField populationField = new javafx.scene.control.TextField(String.valueOf(population));
		populationField.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*")) {
				populationField.setText(oldValue);
			} else {
				try {
					population = Integer.parseInt(newValue);
				} catch (NumberFormatException ex) {
					population = 0;
				}
			}
		});

		Label eliteCountLabel = new Label("Elite Count:");
		javafx.scene.control.TextField eliteCountField = new javafx.scene.control.TextField(String.valueOf(eliteCount));
		eliteCountField.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*")) {
				eliteCountField.setText(oldValue);
			} else {
				try {
					eliteCount = Integer.parseInt(newValue);
				} catch (NumberFormatException ex) {
					eliteCount = 0;
				}
			}
		});

		controls.getChildren().addAll(generationsLabel, generationsField, populationLabel, populationField,
				eliteCountLabel, eliteCountField);

		Label growingBinLabel = new Label("Growing bin");
		javafx.scene.control.CheckBox growingBinCheckBox = new javafx.scene.control.CheckBox();
		growingBinCheckBox.setSelected(growingBin);
		growingBinCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
			growingBin = newValue;
		});
		HBox growingBinHBox = new javafx.scene.layout.HBox(10); // 10 is spacing
		growingBinHBox.setAlignment(Pos.CENTER_LEFT);
		Label axisLabel = new Label("Axis");
		ComboBox<String> axisComboBox = new ComboBox<>();
		axisComboBox.getItems().addAll("x", "y", "z");
		axisComboBox.setValue("x");
		axisComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
			axis = newValue;
		});

		growingBinHBox.getChildren().addAll(growingBinLabel, growingBinCheckBox);
		growingBinHBox.getChildren().addAll(axisLabel, axisComboBox);
		controls.getChildren().add(growingBinHBox);

		statusLabel = new Label("Ready");
		controls.getChildren().add(this.solverComboBox);
		controls.getChildren().add(solveButton);
		controls.getChildren().add(exportButton);
		controls.getChildren().add(statusLabel);
		controls.setStyle(
				"-fx-padding: 20; -fx-background-color: rgba(255, 255, 255, 0.2); -fx-background-radius: 10;");
		controls.setMaxHeight(VBox.USE_PREF_SIZE);
		controls.setMaxWidth(VBox.USE_PREF_SIZE);

		// Wrap controls in a Group or just align them in the StackPane
		StackPane.setAlignment(controls, Pos.CENTER_LEFT);
		StackPane.setMargin(controls, new javafx.geometry.Insets(5));

		root.getChildren().add(controls);

		// Event Handling
		solveButton.setOnAction(e -> runSolver());
		exportButton.setOnAction(e -> exportSolution());

		Scene scene = new Scene(root, 800, 600);
		primaryStage.setTitle("Bin packing solver");
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	private SubScene create3DScene(Group world) {
		world.getChildren().clear();

		SubScene subScene = new SubScene(world, 800, 550, true, javafx.scene.SceneAntialiasing.BALANCED);

		PerspectiveCamera camera = new PerspectiveCamera(true);
		camera.setTranslateZ(100);
		camera.setRotationAxis(Rotate.Y_AXIS);
		camera.setRotate(180);
		camera.setTranslateY(0);
		camera.setTranslateX(0);
		camera.setNearClip(0.1);
		camera.setFarClip(1000.0);
		camera.setFieldOfView(45);

		Group cameraGroup = new Group();
		cameraGroup.getChildren().add(camera);
		cameraGroup.getTransforms().addAll(rotateY, rotateX);
		world.getChildren().add(cameraGroup);

		subScene.setCamera(camera);

		subScene.setOnMousePressed(event -> {
			lastMouseX = event.getSceneX();
			lastMouseY = event.getSceneY();
			subScene.requestFocus();
		});

		subScene.setOnMouseDragged(event -> {
			if (event.isPrimaryButtonDown()) {
				double dx = event.getSceneX() - lastMouseX;
				double dy = event.getSceneY() - lastMouseY;

				rotateY.setAngle(rotateY.getAngle() + dx * 0.5);
				rotateX.setAngle(rotateX.getAngle() + dy * 0.5);
			}
			lastMouseX = event.getSceneX();
			lastMouseY = event.getSceneY();
		});

		subScene.setOnScroll(event -> {
			double delta = -event.getDeltaY();
			double newZ = camera.getTranslateZ() + delta * 0.5;

			camera.setTranslateZ(newZ);
		});
		subScene.setOnKeyPressed(event -> {
			double moveAmount = 5.0;
			switch (event.getCode()) {
				case LEFT:
					camera.setTranslateX(camera.getTranslateX() + moveAmount);
					break;
				case RIGHT:
					camera.setTranslateX(camera.getTranslateX() - moveAmount);
					break;
				default:
					break;
			}
		});

		subScene.setFocusTraversable(true);
		subScene.requestFocus();

		// reference axes
		Box xAxis = new Box(100, 0.5, 0.5);
		xAxis.setMaterial(new PhongMaterial(Color.RED));
		Box yAxis = new Box(0.5, 100, 0.5);
		yAxis.setMaterial(new PhongMaterial(Color.GREEN));
		Box zAxis = new Box(0.5, 0.5, 100);
		zAxis.setMaterial(new PhongMaterial(Color.BLUE));

		world.getChildren().addAll(xAxis, yAxis, zAxis);

		return subScene;
	}

	private void runSolver() {
		statusLabel.setText("Solving...");

		// Generate Data
		List<com.binpacker.lib.common.Box> boxes = generateRandomBoxes(500);
		com.binpacker.lib.common.Bin bin = new com.binpacker.lib.common.Bin(0, binWidthField.getValue(),
				binHeightField.getValue(), binDepthField.getValue());
		// Solve
		Solver solver = solverComboBox.getValue();
		Optimizer optimizer = new GAOptimizer();
		optimizer.initialize(solver, boxes, bin, growingBin, axis, this.population, this.eliteCount);

		Random random = new Random();
		List<Color> boxColors = new ArrayList<>();
		for (int i = 0; i < boxes.size(); i++) {
			boxColors.add(Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
		}

		int generations = this.generations;

		Task<Void> solverTask = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				final Group solverOutputGroup = new Group();
				Platform.runLater(() -> {
					world.getChildren().add(solverOutputGroup);
				});

				for (int i = 0; i < generations; i++) {
					result = optimizer.executeNextGeneration();
					final double rawRate = optimizer.rate(result, bin) * 100;
					final String rate = String.format("%.2f", rawRate);
					final int generation = i + 1;

					Platform.runLater(() -> {
						statusLabel
								.setText("Solving... Generation " + generation + " complete, " + rate + "% full");

						solverOutputGroup.getChildren().clear(); // Clear previous generation's visualization

						int binOffset = -50;
						for (List<com.binpacker.lib.common.Box> binBoxes : result) {
							for (com.binpacker.lib.common.Box spec : binBoxes) {
								Color boxColor = boxColors.get(spec.id % boxColors.size());
								PhongMaterial boxMaterial = new PhongMaterial(boxColor);
								Box box = new Box(spec.size.x, spec.size.y, spec.size.z);
								box.setMaterial(boxMaterial);

								// JavaFX Box is centered at (0,0,0), so we need to offset by half size
								box.setTranslateX(spec.position.x + spec.size.x / 2 + binOffset);
								box.setTranslateY(spec.position.y + spec.size.y / 2);
								box.setTranslateZ(spec.position.z + spec.size.z / 2);

								solverOutputGroup.getChildren().add(box);
							}

							// Draw bin outline
							Box binBox = new Box(bin.w, bin.h, bin.d);
							binBox.setDrawMode(DrawMode.LINE);
							binBox.setMaterial(new PhongMaterial(Color.BLACK));
							binBox.setTranslateX(bin.w / 2 + binOffset);
							binBox.setTranslateY(bin.h / 2);
							binBox.setTranslateZ(bin.d / 2);
							solverOutputGroup.getChildren().add(binBox);

							binOffset += 40; // Space out bins
						}
					});

				}

				return null;
			}
		};
		new Thread(solverTask).start();
	}

	private void exportSolution() {
		if (result == null || result.isEmpty()) {
			statusLabel.setText("No solution to export – run the solver first.");
			return;
		}

		String csv = Utils.exportCsv(result);

		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save Solution CSV");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
		chooser.setInitialFileName("solution.csv");

		File file = chooser.showSaveDialog(primaryStage);
		if (file == null) {
			statusLabel.setText("Export cancelled.");
			return;
		}

		try (FileWriter writer = new FileWriter(file)) {
			writer.write(csv);
			statusLabel.setText("Exported to " + file.getAbsolutePath());
		} catch (IOException e) {
			statusLabel.setText("Failed to export: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private List<com.binpacker.lib.common.Box> generateRandomBoxes(int count) {
		List<com.binpacker.lib.common.Box> boxes = new ArrayList<>();
		Random random = new Random();
		for (int i = 0; i < count; i++) {
			float width = random.nextInt(8) + 4;
			float height = random.nextInt(8) + 4;
			float depth = random.nextInt(8) + 4;
			com.binpacker.lib.common.Box box = new com.binpacker.lib.common.Box(new Point3f(0, 0, 0),
					new Point3f(width, height, depth));
			box.id = i;
			boxes.add(box);
		}
		return boxes;
	}
}
