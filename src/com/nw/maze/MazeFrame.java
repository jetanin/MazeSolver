package com.nw.maze;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;

public class MazeFrame  extends JFrame{
	
	private int canvasWidth;
	private int canvasHeight;
	
	private MazeData data;

	// Controls
	private JComboBox<String> algorithmBox;
	private JButton runButton;
	private JButton resetButton;
	private JSlider speedSlider;
	private JButton importButton;
	private ControlListener controlListener;
	// GA parameter controls
	private javax.swing.JSpinner gaPopSpinner;
	private javax.swing.JSpinner gaGenSpinner;
	private javax.swing.JSpinner gaMutationSpinner;
	private javax.swing.JSpinner gaGoalBiasSpinner;
	private javax.swing.JSpinner gaElitismSpinner;
	// Metrics labels
	private JLabel costLabel;
	private JLabel stepsLabel;
	private JLabel visitedLabel;
	private JLabel timeLabel;
	// Maze file label
	private JLabel mazeFileLabel;
	// removed Route Weight label per request
	
	public MazeFrame(String title, int canvasWidth, int canvasHeight) {
		super(title);
		this.canvasWidth = canvasWidth;
		this.canvasHeight = canvasHeight;

		// Build UI
		MazeCanvas canvas = new MazeCanvas();
		JPanel root = new JPanel(new BorderLayout());
		root.add(buildControlPanel(), BorderLayout.NORTH);
		root.add(canvas, BorderLayout.CENTER);
		this.setContentPane(root);

		// keep a reference for resizing
		this.canvasRef = canvas;
		
		this.pack();
		this.setResizable(true);
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setVisible(true);
	}
	
	public void render(MazeData data) {
		this.data = data;
		repaint();
	}

	public void setControlListener(ControlListener listener) {
		this.controlListener = listener;
	}

	public void setControlsEnabled(boolean enabled) {
		if (algorithmBox != null) algorithmBox.setEnabled(enabled);
		if (runButton != null) runButton.setEnabled(enabled);
	}

	private JPanel buildControlPanel() {
		JPanel panel = new JPanel();
		panel.add(new JLabel("Algorithm:"));
		this.algorithmBox = new JComboBox<>(new String[]{
			"Genetic", "Dijkstra", "A*", "BFS" 
		});
		panel.add(algorithmBox);

		importButton = new JButton("Import Maze...");
		importButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser(new File("."));
				int res = chooser.showOpenDialog(MazeFrame.this);
				if (res == JFileChooser.APPROVE_OPTION && controlListener != null) {
					File f = chooser.getSelectedFile();
					if (f != null) controlListener.onImportRequested(f.getAbsolutePath());
				}
			}
		});
		panel.add(importButton);
		mazeFileLabel = new JLabel("File: (none)");
		panel.add(mazeFileLabel);
		runButton = new JButton("Run");
		runButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (controlListener != null) {
					String name = (String) algorithmBox.getSelectedItem();
					controlListener.onRunRequested(name);
				}
			}
		});
		panel.add(runButton);
		resetButton = new JButton("Reset");
		resetButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (controlListener != null) {
					controlListener.onResetRequested();
				}
			}
		});
		panel.add(resetButton);

		panel.add(new JLabel("Speed:"));
		speedSlider = new JSlider(0, 100, 30); // delay ms
		speedSlider.setMajorTickSpacing(50);
		speedSlider.setMinorTickSpacing(10);
		speedSlider.setPaintTicks(true);
		panel.add(speedSlider);


		// Metrics display
		costLabel = new JLabel("Cost: -");
		stepsLabel = new JLabel("Steps: -");

		// Genetic parameters (visible always for simplicity)
		panel.add(new JLabel("GA Pop:"));
		gaPopSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(140, 10, 10000, 10));
		panel.add(gaPopSpinner);

		panel.add(new JLabel("GA Gen:"));
		gaGenSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(300, 10, 100000, 10));
		panel.add(gaGenSpinner);

		panel.add(new JLabel("GA Mut%:"));
		gaMutationSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(5.0, 0.0, 100.0, 0.5));
		panel.add(gaMutationSpinner);

		panel.add(new JLabel("GA GoalBias%:"));
		gaGoalBiasSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(80.0, 0.0, 100.0, 1.0));
		panel.add(gaGoalBiasSpinner);

		panel.add(new JLabel("GA Elitism Count:"));
		gaElitismSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(14, 1, 10000, 1));
		panel.add(gaElitismSpinner);
		visitedLabel = new JLabel("Visited: -");
		timeLabel = new JLabel("Time: -ms");
		panel.add(costLabel);
		panel.add(stepsLabel);
		panel.add(visitedLabel);
		panel.add(timeLabel);
		return panel;
	}

	public void setMazeFileName(String filePath) {
		String name = filePath;
		try {
			if (filePath != null) {
				java.io.File f = new java.io.File(filePath);
				name = f.getName();
			}
		} catch (Throwable ignored) {}
		if (mazeFileLabel != null) {
			mazeFileLabel.setText("File: " + (name != null ? name : "(none)"));
		}
	}

	// Accessors for GA parameters
	public int getGaPopulation() {
		Object v = gaPopSpinner != null ? gaPopSpinner.getValue() : 140;
		return (v instanceof Number) ? ((Number)v).intValue() : 140;
	}

	public int getGaGenerations() {
		Object v = gaGenSpinner != null ? gaGenSpinner.getValue() : 300;
		return (v instanceof Number) ? ((Number)v).intValue() : 300;
	}

	public double getGaMutationRate() {
		Object v = gaMutationSpinner != null ? gaMutationSpinner.getValue() : 5.0;
		double pct = (v instanceof Number) ? ((Number)v).doubleValue() : 5.0;
		return Math.max(0.0, Math.min(1.0, pct / 100.0));
	}

	public double getGaGoalBias() {
		Object v = gaGoalBiasSpinner != null ? gaGoalBiasSpinner.getValue() : 80.0;
		double pct = (v instanceof Number) ? ((Number)v).doubleValue() : 80.0;
		return Math.max(0.0, Math.min(1.0, pct / 100.0));
	}

	public int getGaElitismCount() {
		Object v = gaElitismSpinner != null ? gaElitismSpinner.getValue() : 14;
		return (v instanceof Number) ? ((Number)v).intValue() : 14;
	}

	// Reset GA parameter controls to their default values
	public void resetGaParametersToDefaults() {
		if (gaPopSpinner != null) gaPopSpinner.setValue(140);
		if (gaGenSpinner != null) gaGenSpinner.setValue(300);
		if (gaMutationSpinner != null) gaMutationSpinner.setValue(5.0);
		if (gaGoalBiasSpinner != null) gaGoalBiasSpinner.setValue(80.0);
		if (gaElitismSpinner != null) gaElitismSpinner.setValue(14);
	}
	
	public void paint(MazeUtil util) {
		int cw = (canvasRef != null ? canvasRef.getWidth() : canvasWidth);
		int ch = (canvasRef != null ? canvasRef.getHeight() : canvasHeight);
		int w = cw / Math.max(1, data.M());
		int h = ch / Math.max(1, data.N());
		for(int i = 0; i < data.N(); i++) {
			for(int j = 0; j < data.M(); j++) {
				if(data.getMazeChar(i, j) == MazeData.WALL) {
					util.setColor(MazeUtil.LightBlue);
				}else {
					util.setColor(MazeUtil.White);
				}
				if(data.path[i][j]) {
					util.setColor(MazeUtil.Yellow);
				}
				if(data.result[i][j]) {
					util.setColor(MazeUtil.Red);
				}
				util.fillRectangle(j * w, i * h, w, h);

				// Draw S/G for start/goal; else draw weight for road cells
				if (i == data.getEntranceX() && j == data.getEntranceY()) {
					util.setColor(Color.BLACK);
					util.drawCenteredString("S", j * w, i * h, w, h);
				} else if (i == data.getExitX() && j == data.getExitY()) {
					util.setColor(Color.BLACK);
					util.drawCenteredString("G", j * w, i * h, w, h);
				} else if (data.getMazeChar(i, j) == MazeData.ROAD && data.weight != null && data.weight[i][j] > 0) {
					util.setColor(Color.BLACK);
					util.drawCenteredString(Integer.toString(data.weight[i][j]), j * w, i * h, w, h);
				}
			}
		}
	}
	
	private class MazeCanvas extends JPanel{

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			MazeUtil util = MazeUtil.getInstance(g);
			if(data != null) {
				MazeFrame.this.paint(util);
			}
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(canvasWidth, canvasHeight);
		}
	}

	// reference to canvas for resizing
	private MazeCanvas canvasRef;

	public static interface ControlListener {
		void onRunRequested(String algorithmName);
		void onResetRequested();
		void onImportRequested(String filePath);
	}

	public int getDelayMs() {
		return speedSlider != null ? speedSlider.getValue() : 10;
	}

	public void updateMetrics(Integer cost, Integer steps, Integer visited, Long timeMs, String algoName) {
		if (algoName != null) {
			setTitle("Maze Solver - " + algoName);
		}
		if (costLabel != null) costLabel.setText("Cost: " + (cost != null ? cost-1 : "-"));
		if (stepsLabel != null) stepsLabel.setText("Steps: " + (steps != null ? steps : "-"));
		if (visitedLabel != null) visitedLabel.setText("Visited: " + (visited != null ? visited : "-"));
		if (timeLabel != null) timeLabel.setText("Time: " + (timeMs != null ? timeMs : "-") + "ms");
	}

	public int getBlockSize() {
		int cols = (data != null ? data.M() : 1);
		int cw = (canvasRef != null ? canvasRef.getWidth() : canvasWidth);
		return Math.max(1, cw / Math.max(1, cols));
	}

	public void resizeToBlock(int blockSize) {
		// Decouple app frame size from block size: do not resize frame/canvas.
		// Keep current frame size; just trigger a repaint if needed.
		this.repaint();
	}
}
