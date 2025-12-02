package com.performetriks.performator.http.converter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.performetriks.performator.base.PFR;

/*****************************************************************************
 * A Swing application that loads a HAR (HTTP Archive) and generates Java code that uses
 * com.performetriks.performator.http.PFRHttp to reproduce the HTTP requests.
 *
 * Features:
 * - fullscreen JFrame
 * - left pane (50%) containing all input controls
 * - right pane (50%) containing a textarea with generated Java code (copy/paste)
 * - parses a HAR file using Google Gson into an inner model
 * - all inputs show description tooltips on mouseover (decorator)
 * - every change to any input re-generates the output
 *
 * NOTE: This is a single-file demonstration. The generated Java code in the right pane is
 * textual output and not compiled/executed by this application.
 *****************************************************************************/
public class PFRHttpConvertHar extends JFrame {

	private static final long serialVersionUID = 1L;
	
	// ---------------------------
	// UI Elements (left / right)
	// ---------------------------
	private final JTextPane outputArea = new JTextPane();
	private final JButton btnChooseHar = new JButton("Choose HAR...");
	private final JLabel lblHarPath = new JLabel("No file chosen");

	private final JCheckBox cbExcludeRedirects = new JCheckBox("Exclude Redirects", true);
	private final JCheckBox cbExcludeCss = new JCheckBox("Exclude CSS", true);
	private final JCheckBox cbExcludeScripts = new JCheckBox("Exclude Scripts", true);
	private final JCheckBox cbExcludeImages = new JCheckBox("Exclude Images", true);
	private final JCheckBox cbExcludeFonts = new JCheckBox("Exclude Fonts", true);
	private final JTextField tfExcludeRegex = new JTextField(".*.fileA,.*fileB");

	private final JCheckBox cbSeparateResponses = new JCheckBox("Separate Responses", false);
	private final JCheckBox cbSeparateRequests = new JCheckBox("Separate Requests", false);
	private final JCheckBox cbSeparateHeaders = new JCheckBox("Separate Headers", true);
	private final JCheckBox cbSeparateParameters = new JCheckBox("Separate Parameters", false);
	private final JCheckBox cbSurroundTryCatch = new JCheckBox("Surround with try-catch", true);

	private final JCheckBox cbAddCheckStatusEquals = new JCheckBox("Add checkStatusEquals(200)", true);
	private final JCheckBox cbAddCheckBodyContains = new JCheckBox("Add checkBodyContains(\"\")", true);
	private final JCheckBox cbAddMeasureSize = new JCheckBox("Add measureSize(ByteSize.KB)", false);
	private final JCheckBox cbAddThrowOnFail = new JCheckBox("Add throwOnFail()", true);

	private final JCheckBox cbDebugLogOnFail = new JCheckBox("Debug Log On Fail", true);
	private final JSpinner spDefaultResponseTimeout = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
	private final JSpinner spDefaultPause = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));

	private final JRadioButton rbSlaGlobal = new JRadioButton("Global", true);
	private final JRadioButton rbSlaPerRequest = new JRadioButton("Per Request", false);
	private final JRadioButton rbSlaNone = new JRadioButton("None", false);

	// Parsed HAR model (in-memory)
	private RequestModel requestModel = new RequestModel();

	/*****************************************************************************
	 * Main entrypoint: create and show the UI.
	 *
	 * @param args not used
	 *****************************************************************************/
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			PFRHttpConvertHar g = new PFRHttpConvertHar();
			g.setVisible(true);
		});
	}

	/*****************************************************************************
	 * Constructor: sets up the JFrame and initializes UI components.
	 *****************************************************************************/
	public PFRHttpConvertHar() {
		super("HAR to Performator Converter");
		initializeFrame();
		initializeUI();
		attachListeners();
	}

	/*****************************************************************************
	 * Initialize the main frame (fullscreen and close behavior).
	 *****************************************************************************/
	private void initializeFrame() {
		// Start in fullscreen mode
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setExtendedState(JFrame.MAXIMIZED_BOTH);
		setLayout(new BorderLayout());
		setMinimumSize(new Dimension(800, 600));
	}

	/*****************************************************************************
	 * Initialize UI: left input panel and right output area laid out 50/50.
	 *****************************************************************************/
	private void initializeUI() {
        
		//-----------------------------------
		// Make Quick Dark Mode
		try {
			UIManager.setLookAndFeel(new NimbusLookAndFeel());
		} catch (UnsupportedLookAndFeelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Color reallyDark = new Color(20, 20, 20);
		Color reallyLight = new Color(230, 230, 230);
		
        // tweak some UIManager colors
        UIManager.put("control", reallyDark);
        UIManager.put("text", reallyLight);
        UIManager.put("info", new Color(60, 60, 60));
        UIManager.put("nimbusBase", reallyDark);
        UIManager.put("nimbusAlertYellow", new Color(248, 187, 0));
        UIManager.put("nimbusDisabledText", new Color(180, 180, 180));
        UIManager.put("nimbusFocus", new Color(115, 164, 209));
        UIManager.put("nimbusGreen", new Color(176, 179, 50));
        UIManager.put("nimbusInfoBlue", new Color(66, 139, 221));
        UIManager.put("nimbusLightBackground", new Color(30, 30, 30));
        UIManager.put("nimbusOrange", new Color(191, 98, 4));
        UIManager.put("nimbusRed", new Color(169, 46, 34));
        UIManager.put("nimbusSelectedText", Color.WHITE);
        UIManager.put("nimbusSelectionBackground", new Color(75, 110, 175));
       
        
		// Create left panel with inputs
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BorderLayout());
		leftPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Top: file chooser area
		JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		filePanel.add(btnChooseHar);
		filePanel.add(lblHarPath);
		leftPanel.add(filePanel, BorderLayout.NORTH);

		// Middle: inputs in a scroll pane
		JPanel inputs = new JPanel();
		inputs.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;

		// Helper to add labeled components with tooltips (decorator)
		BiConsumer<JComponent, String> addWithDesc = (comp, desc) -> {
			comp.setToolTipText("<html>" + desc + "</html>");
			c.gridx = 0;
			inputs.add(comp instanceof JCheckBox ? comp : new JLabel(comp.getName() == null ? "" : comp.getName()), c);
		};

		// We'll add rows manually with label and component
		// Row: Exclude Redirects
		addLabeled(inputs, c,  "Exclude Redirects", cbExcludeRedirects,
				"Checkbox to disable auto following HTTP redirects should be included in the script. " +
						"If unchecked, the generated request builder will include .disableFollowRedirects().", 0);
		addLabeled(inputs, c,  "Exclude CSS", cbExcludeCss,
				"Do not generate code for requests that have _resourceType=stylesheet.", 1);
		addLabeled(inputs, c,  "Exclude Scripts", cbExcludeScripts,
				"Do not generate code for requests that have _resourceType=script.", 2);
		addLabeled(inputs, c,  "Exclude Images", cbExcludeImages,
				"Do not generate code for requests that have image types in _resourceType.", 3);
		addLabeled(inputs, c,  "Exclude Fonts", cbExcludeFonts,
				"Do not generate code for requests that have _resourceType=font.", 4);

		// Exclude Regex
		JLabel lblExcludeRegex = new JLabel("Exclude Regex (comma separated):");
		lblExcludeRegex.setToolTipText("Excludes any request whose URL matches any of the provided regular expressions.");
		c.gridx = 0;
		c.gridy++;
		inputs.add(lblExcludeRegex, c);
		c.gridx = 1;
		tfExcludeRegex.setToolTipText("Comma separated regex list to exclude requests by URL. Default: .*.js,.*.css,.*.svg");
		inputs.add(tfExcludeRegex, c);

		// Other toggles
		addLabeled(inputs, c, "Separate Responses", cbSeparateResponses,
				"If selected, each response gets it's own PFRHttpResponse instance.", 6);
		addLabeled(inputs, c, "Separate Requests", cbSeparateRequests,
				"If selected, HTTP requests will be generated into separate methods (one per request) and called from execute().", 7);
		addLabeled(inputs, c, "Separate Headers", cbSeparateHeaders,
				"If selected, headers will be emitted in separate getHeaders*() methods; otherwise they are inline as .header(...).", 8);
		addLabeled(inputs, c, "Separate Parameters", cbSeparateParameters,
				"If selected, parameters will be emitted in separate getParams*() methods; otherwise they are inline as .param(...).", 9);
		addLabeled(inputs, c, "Surround with try-catch", cbSurroundTryCatch,
				"If selected, execute() will include try-catch around the requests.", 10);

		// Checks / placeholders
		addLabeled(inputs, c, "Add checkBodyContains(\"\")", cbAddCheckBodyContains,
				"Add a placeholder checkBodyContains(\"\") in the generated request builder chain.", 11);
		addLabeled(inputs, c, "Add checkStatusEquals(200)", cbAddCheckStatusEquals,
				"Add a placeholder checkStatusEquals(200) in the generated request builder chain.", 12);
		addLabeled(inputs, c, "Add measureSize(ByteSize.KB)", cbAddMeasureSize,
				"Add a placeholder measureSize(ByteSize.KB) in the generated request builder chain.", 13);
		addLabeled(inputs, c, "Add throwOnFail()", cbAddThrowOnFail,
				"Add throwOnFail() after .send() in the generated chain.", 14);

		// Other general options
		addLabeled(inputs, c, "Debug Log On Fail", cbDebugLogOnFail,
				"If selected, PFRHttp.debugLogFail(true); will be emitted in initializeUser().", 15);

		// Numeric spinners
		JLabel lblRespTO = new JLabel("Default Response Timeout (s) [0 = none]:");
		lblRespTO.setToolTipText("If > 0 then PFRHttp.defaultResponseTimeout(Duration.ofSeconds(value)) will be added.");
		c.gridx = 0;
		c.gridy++;
		inputs.add(lblRespTO, c);
		c.gridx = 1;
		inputs.add(spDefaultResponseTimeout, c);

		JLabel lblPause = new JLabel("Default Pause (ms) [0 = none]:");
		lblPause.setToolTipText("If > 0 then PFRHttp.defaultPause(value) will be added.");
		c.gridx = 0;
		c.gridy++;
		inputs.add(lblPause, c);
		c.gridx = 1;
		inputs.add(spDefaultPause, c);

		// SLA radio buttons
		JLabel lblSla = new JLabel("Default SLA:");
		lblSla.setToolTipText("Global: add a DEFAULT_SLA constant. Per Request: add a .sla(...) per request. None: do nothing.");
		c.gridx = 0;
		c.gridy++;
		inputs.add(lblSla, c);
		c.gridx = 1;
		JPanel pSla = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		ButtonGroup bgSla = new ButtonGroup();
		bgSla.add(rbSlaGlobal);
		bgSla.add(rbSlaPerRequest);
		bgSla.add(rbSlaNone);
		pSla.add(rbSlaGlobal);
		pSla.add(rbSlaPerRequest);
		pSla.add(rbSlaNone);
		inputs.add(pSla, c);

		//------------------------------------
		// Place inputs into scroll pane
		JScrollPane scrollInputs = new JScrollPane(inputs);
		leftPanel.add(scrollInputs, BorderLayout.CENTER);
		

		//------------------------------------
		// Configure output area
		outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		outputArea.setBackground(reallyDark);    
		outputArea.setForeground(reallyLight);   
		outputArea.setCaretColor(reallyLight);   
		//outputArea.setLineWrap(false);
		//outputArea.setWrapStyleWord(false);
		//outputArea.setTabSize(4);
		
		FontMetrics fm = outputArea.getFontMetrics(outputArea.getFont());
		int charWidth = fm.charWidth(' ');
		int tabWidth = charWidth * 4; // 4-space tab

		TabStop[] stops = new TabStop[20];
		for (int i = 0; i < stops.length; i++) {
		    stops[i] = new TabStop((i + 1) * tabWidth);
		}
		TabSet tabSet = new TabSet(stops);

		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setTabSet(attrs, tabSet);

		StyledDocument doc = outputArea.getStyledDocument();
		doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
		
		outputArea.setText("// Load a HAR file to generate code...");
		
		//------------------------------------
		// Right panel: output
		JPanel rightPanel = new JPanel(new BorderLayout());
		rightPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		
		JScrollPane outputScroll = new JScrollPane(outputArea);
		rightPanel.add(outputScroll, BorderLayout.CENTER);
		
		//------------------------------------
		// Split pane 50/50
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
		split.setResizeWeight(0.70);
		add(split, BorderLayout.CENTER);

		

	}

	/*****************************************************************************
	 * Adds a labeled component in the inputs panel using GridBag constraints.
	 *
	 * @param inputs the panel to add to
	 * @param c GridBagConstraints used/modified
	 * @param label text label
	 * @param comp the component to place (checkbox or field)
	 * @param nextRow the next row number (used to increment)
	 *****************************************************************************/
	private void addLabeled(JPanel inputs, GridBagConstraints c, String label, JComponent comp, String tooltip, int nextRow) {
		JLabel lbl = new JLabel(label + ":");
		lbl.setToolTipText(tooltip);
		c.gridx = 0;
		c.gridy = nextRow;
		c.weightx = 0.3;
		inputs.add(lbl, c);
		c.gridx = 1;
		c.weightx = 0.7;
		comp.setToolTipText(tooltip);
		inputs.add(comp, c);
	}

	/*****************************************************************************
	 * Attach listeners to all input components so that any change triggers regeneration.
	 *****************************************************************************/
	private void attachListeners() {
		// All checkboxes/spinners/textfields should trigger regeneration on change
		ItemListener itemListener = e -> regenerateCode();
		ChangeListenerForSpinner changeListenerForSpinner = new ChangeListenerForSpinner();
		ActionListener actionListener = e -> regenerateCode();

		cbExcludeRedirects.addItemListener(itemListener);
		cbExcludeCss.addItemListener(itemListener);
		cbExcludeScripts.addItemListener(itemListener);
		cbExcludeImages.addItemListener(itemListener);
		cbExcludeFonts.addItemListener(itemListener);
		cbSeparateResponses.addItemListener(itemListener);
		cbSeparateRequests.addItemListener(itemListener);
		cbSeparateHeaders.addItemListener(itemListener);
		cbSeparateParameters.addItemListener(itemListener);
		cbSurroundTryCatch.addItemListener(itemListener);
		cbAddCheckBodyContains.addItemListener(itemListener);
		cbAddCheckStatusEquals.addItemListener(itemListener);
		cbAddMeasureSize.addItemListener(itemListener);
		cbAddThrowOnFail.addItemListener(itemListener);
		cbDebugLogOnFail.addItemListener(itemListener);
		spDefaultResponseTimeout.addChangeListener(changeListenerForSpinner);
		spDefaultPause.addChangeListener(changeListenerForSpinner);

		tfExcludeRegex.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) { regenerateCode(); }
			public void removeUpdate(DocumentEvent e) { regenerateCode(); }
			public void insertUpdate(DocumentEvent e) { regenerateCode(); }
		});

		rbSlaGlobal.addActionListener(actionListener);
		rbSlaPerRequest.addActionListener(actionListener);
		rbSlaNone.addActionListener(actionListener);

		// File chooser
		btnChooseHar.addActionListener(e -> chooseHarFile());

		// When user manually edits output, we don't need to regenerate; so no listener there.

		// Window resize -> keep split 50/50; add component listener to recompute divider
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				// enforce approx 50/50 split on resize
				SwingUtilities.invokeLater(() -> {
					if (getContentPane().getComponentCount() > 0 && getContentPane().getComponent(0) instanceof JSplitPane) {
						JSplitPane sp = (JSplitPane)getContentPane().getComponent(0);
						int w = getWidth();
						sp.setDividerLocation(w/2);
					}
				});
			}
		});
	}

	/*****************************************************************************
	 * Handles HAR file selection and parsing.
	 *****************************************************************************/
	private void chooseHarFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("HAR files", "har"));
		int res = chooser.showOpenDialog(this);
		if (res == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			lblHarPath.setText(f.getAbsolutePath());
			try {
				parseHarFile(f);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this, "Failed to parse HAR: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				requestModel = new RequestModel(); // reset model
			}
			regenerateCode();
		}
	}

	/*****************************************************************************
	 * Parse the HAR file into an in-memory HarModel.
	 *
	 * This loads the JSON with GSON and extracts only the fields we need:
	 * - request url
	 * - request method
	 * - request headers
	 * - request postData/text (if present)
	 * - any custom _resourceType from the HAR entry
	 *
	 * @param file HAR file to parse
	 * @throws IOException on IO problems
	 *****************************************************************************/
	private void parseHarFile(File file) throws IOException {
		try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			JsonElement rootEl = JsonParser.parseReader(r);
			JsonObject root = rootEl.isJsonObject() ? rootEl.getAsJsonObject() : new JsonObject();
			JsonObject log = root.has("log") && root.get("log").isJsonObject() ? root.getAsJsonObject("log") : null;
			List<RequestEntry> entries = new ArrayList<>();
			if (log != null && log.has("entries") && log.get("entries").isJsonArray()) {
				JsonArray arr = log.getAsJsonArray("entries");
				for (JsonElement el : arr) {
					if (!el.isJsonObject()) continue;
					JsonObject entry = el.getAsJsonObject();
					JsonObject req = entry.has("request") && entry.get("request").isJsonObject() ? entry.getAsJsonObject("request") : null;
					if (req == null) continue;

					RequestEntry hre = new RequestEntry();
					hre.method = req.has("method") ? req.get("method").getAsString() : "GET";
					hre.setURL( req.has("url") ? req.get("url").getAsString() : "");
					
					//--------------------------------------
					// headers
					hre.headers = new LinkedHashMap<>();
					if (req.has("headers") && req.get("headers").isJsonArray()) {
						JsonArray hdrs = req.getAsJsonArray("headers");
						for (JsonElement h : hdrs) {
							if (!h.isJsonObject()) continue;
							JsonObject ho = h.getAsJsonObject();
							String name = ho.has("name") ? ho.get("name").getAsString() : "";
							String value = ho.has("value") ? ho.get("value").getAsString() : "";
							if (!name.isEmpty()) hre.headers.put(name, value);
						}
					}
					
					//--------------------------------------
					// postData
					if (req.has("postData") && req.get("postData").isJsonObject()) {
						JsonObject pd = req.getAsJsonObject("postData");
						if (pd.has("text")) {
							hre.postData = pd.get("text").getAsString();
						}
						// try to extract params if available
						if (pd.has("params") && pd.get("params").isJsonArray()) {
							JsonArray params = pd.getAsJsonArray("params");
							hre.params = new LinkedHashMap<>();
							for (JsonElement p : params) {
								if (!p.isJsonObject()) continue;
								JsonObject po = p.getAsJsonObject();
								String name = po.has("name") ? po.get("name").getAsString() : null;
								String value = po.has("value") ? po.get("value").getAsString() : null;
								if (name != null) hre.params.put(name, value == null ? "" : value);
							}
						}
					}
					
					//--------------------------------------
					// custom _resourceType: sometimes present in _resourceType or in comment/other
					String resourceType = "";
					if (entry.has("_resourceType")) {
						resourceType = entry.get("_resourceType").getAsString();
					} else if (entry.has("cache") && entry.get("cache").isJsonObject()) {
						// nothing
					} else if (req.has("_resourceType")) {
						resourceType = req.get("_resourceType").getAsString();
					} else if (entry.has("comment")) {
						resourceType = entry.get("comment").getAsString();
					}
					hre.resourceType = resourceType;
					entries.add(hre);
				}
			}
			requestModel = new RequestModel(entries);
		}
	}

	/*****************************************************************************
	 * Regenerate the code in the output textarea based on current inputs and the loaded HAR model.
	 *
	 * This method is called whenever any input is changed (or the HAR is loaded).
	 *****************************************************************************/
	private void regenerateCode() {
		String code = generateFullClassCode();
		outputArea.setText(code);
		outputArea.setCaretPosition(0);
	}

	/*****************************************************************************
	 * Build the full Java source code string that will be shown in the right-hand textarea.
	 *
	 * This method inspects:
	 * - selected filters (exclude css/scripts/images/fonts)
	 * - exclude regex list
	 * - separate headers/params/requests toggles
	 * - placeholder checks and options
	 *
	 * @return a string containing the generated Java source file
	 *****************************************************************************/
	private String generateFullClassCode() {
		StringBuilder sb = new StringBuilder();

		// Package & imports
		sb.append("package com.performetriks.performator.quickstart.usecase;\n\n");
		sb.append("import java.util.ArrayList;\n");
		sb.append("import java.util.LinkedHashMap;\n");
		sb.append("import java.util.HashMap;\n");
		sb.append("import java.util.List;\n");
		sb.append("import java.util.Map;\n");
		sb.append("import java.time.Duration;\n");
		sb.append("\n");
		sb.append("import com.performetriks.performator.base.PFRUsecase;\n");
		sb.append("import com.performetriks.performator.http.PFRHttp;\n");
		sb.append("import com.performetriks.performator.http.PFRHttpResponse;\n");
		sb.append("import com.performetriks.performator.http.ResponseFailedException;\n");
		sb.append("import com.xresch.hsr.stats.HSRRecordStats.HSRMetric;\n");
		sb.append("import com.xresch.hsr.stats.HSRExpression.Operator;\n");
		sb.append("import com.xresch.hsr.stats.HSRSLA;\n");
		sb.append("import com.xresch.hsr.utils.ByteSize;\n");
		sb.append("\n");

		// Class header & optional global SLA
		boolean slaGlobal = rbSlaGlobal.isSelected();
		if (slaGlobal) {
			sb.append("public class UsecaseFromHar extends PFRUsecase {\n\n");
			sb.append("	private static final HSRSLA DEFAULT_SLA = new HSRSLA(HSRMetric.failrate, Operator.LT, 5);\n\n");
		} else {
			sb.append("public class UsecaseFromHar extends PFRUsecase {\n\n");
		}

		// initializeUser method
		sb.append("	/***************************************************************************\n");
		sb.append("	 * initializeUser\n");
		sb.append("	 ***************************************************************************/\n");
		sb.append("	@Override\n");
		sb.append("	public void initializeUser() {\n");
		if ((Integer) spDefaultResponseTimeout.getValue() > 0) {
			sb.append("		PFRHttp.defaultResponseTimeout(Duration.ofSeconds(").append(spDefaultResponseTimeout.getValue()).append("));\n");
		}
		if ((Integer) spDefaultPause.getValue() > 0) {
			sb.append("		PFRHttp.defaultPause(").append(spDefaultPause.getValue()).append(");\n");
		}
		if (cbDebugLogOnFail.isSelected()) {
			sb.append("		PFRHttp.debugLogFail(true);\n");
		}
		sb.append("	}\n\n");

		// execute signature
		sb.append("	/***************************************************************************\n");
		sb.append("	 * execute\n");
		sb.append("	 ***************************************************************************/\n");
		sb.append("	@Override\n");
		sb.append("	public void execute() throws Throwable {\n");
		sb.append("		PFRHttp.clearCookies();\n\n");

		// try-catch around the body?
		boolean surroundTry = cbSurroundTryCatch.isSelected();
		if (surroundTry) {
			sb.append("		try {\n");
		}

		// Iterate requests based on filters
		List<RequestEntry> requests = filterRequests();

		// If separateRequests true -> generate methods and call them
		boolean separateResponses = cbSeparateResponses.isSelected();
		boolean separateRequests = cbSeparateRequests.isSelected();
		boolean separateHeaders = cbSeparateHeaders.isSelected();
		boolean separateParams = cbSeparateParameters.isSelected();
		
		String postfix = "\r\n\t\t\t";
		//----------------------------------------
		// Separate Variable
		if( ! separateResponses ) {
			sb.append(postfix).append("PFRHttpResponse r = null;\r\n");
		}
		
		int idx = 0;
		for (RequestEntry req : requests) {
			
			String responseVar = "r";

			String name = req.indexedName(idx);
						
			//----------------------------------------
			// Header
			sb.append(postfix).append("//---------------------------------------------");
			sb.append(postfix).append("// ");
			sb.append(postfix).append("//---------------------------------------------");
			
			//----------------------------------------
			// Print Variable
			if( ! cbSeparateResponses.isSelected() ) {
				sb.append(postfix).append("r = ");
			}else {
				responseVar += threeDigits(idx);
				sb.append(postfix).append("PFRHttpResponse ").append(responseVar).append(" = ");
			}
			
			//----------------------------------------
			// Separate Requests
			if (separateRequests) {
				sb.append("r"+name).append("();");
			} else {
				sb.append(generateRequestBuilderBody(req, idx, separateHeaders, separateParams));
			}
			
			//----------------------------------
			// Optional Success handling
			sb.append("\r\n");
			sb.append(postfix).append("// if (!").append(responseVar).append(".isSuccess()) { return; }\r\n");

			idx++;
		}

		if (surroundTry) {
			sb.append("		} catch(ResponseFailedException e) {\n");
			sb.append("			// custom handling of request failures\n");
			sb.append("			throw e;\n");
			sb.append("		}\n");
		}

		sb.append("	}\n\n");

		// If separateRequests, create separate methods
		if (separateRequests) {
			idx = 0;
			for (RequestEntry req : requests) {
				String methodName = "r"+ req.indexedName(idx);
				sb.append("	/***************************************************************************\n");
				sb.append("	 * \n");
				sb.append("	 ***************************************************************************/\n");
				sb.append("	private PFRHttpResponse ").append(methodName).append("() throws ResponseFailedException {\n");
				sb.append("		return "+generateRequestBuilderBody(req, idx, separateHeaders, separateParams));
				sb.append("\n	}\n\n");
				idx++;
			}
		}

		// Add header methods if separateHeaders true
		if (separateHeaders) {
			int hidx = 0;
			for (RequestEntry req : requests) {
				if (req.headers != null && !req.headers.isEmpty()) {
					sb.append("	/***************************************************************************\n");
					sb.append("	 * \n");
					sb.append("	 ***************************************************************************/\n");
					sb.append("	private LinkedHashMap<String,String> getHeaders_").append( threeDigits(hidx) ).append("() {\n");
					sb.append("		LinkedHashMap<String,String> headers = new LinkedHashMap<>();\n\n");
					for (Map.Entry<String, String> e : req.headers.entrySet()) {
						sb.append("		headers.put(\"").append(escape(e.getKey())).append("\", \"").append(escape(e.getValue())).append("\");\n");
					}
					sb.append("		return headers;\n");
					sb.append("	}\n\n");
					hidx++;
				}
			}
		}

		// Add params methods if separateParams true
		if (separateParams) {
			int pidx = 0;
			for (RequestEntry req : requests) {
				if (req.params != null && !req.params.isEmpty()) {
					sb.append("	/***************************************************************************\n");
					sb.append("	 * \n");
					sb.append("	 ***************************************************************************/\n");
					sb.append("	private LinkedHashMap<String,String> getParams_").append( threeDigits(pidx) ).append("() {\n");
					sb.append("		LinkedHashMap<String,String> params = new LinkedHashMap<>();\n");
					for (Map.Entry<String, String> e : req.params.entrySet()) {
						sb.append("		params.put(\"").append(escape(e.getKey())).append("\", \"").append(escape(e.getValue())).append("\");\n");
					}
					sb.append("		return params;\n");
					sb.append("	}\n\n");
					pidx++;
				}
			}
		}

		// terminate method
		sb.append("	/***************************************************************************\n");
		sb.append("	 * terminate\n");
		sb.append("	 ***************************************************************************/\n");
		sb.append("	@Override\n");
		sb.append("	public void terminate() {\n");
		sb.append("		// nothing to clean up\n");
		sb.append("	}\n\n");

		sb.append("}\n");

		return sb.toString();
	}

	/*****************************************************************************
	 * Generates the builder body for a request (used for inline and separate methods).
	 *
	 * @param req request data
	 * @param idx unique index
	 * @param separateHeaders whether headers are emitted in separate method
	 * @param separateParams whether params are emitted in separate method
	 * @return string with method body
	 *****************************************************************************/
	private String generateRequestBuilderBody(RequestEntry req, int idx, boolean separateHeaders, boolean separateParams) {
		StringBuilder sb = new StringBuilder();

		String postfix = "\r\n\t\t\t";
		
		
		
		sb.append("PFRHttp.create(\"").append(req.indexedName(idx)).append("\", \"").append(escape(req.url)).append("\")");
		
		//----------------------------------
		// SLA 
		if (rbSlaPerRequest.isSelected()) {
			sb.append(postfix+"\t").append(".sla(HSRMetric.p90, Operator.LTE, 5555)");
		} else if (rbSlaGlobal.isSelected()) {
			sb.append(postfix+"\t").append(".sla(DEFAULT_SLA)");
		}
		
		//----------------------------------
		// method
		
		if(req.method.equals("GET")
		|| req.method.equals("POST")
		|| req.method.equals("PUT")
		|| req.method.equals("DELETE")
		){
			sb.append(postfix).append("\t.").append(req.method.toUpperCase()).append("()");
		} else {
			sb.append(postfix).append("\t.METHOD(\"").append(req.method.toUpperCase()).append("\")");
		}
		
		//----------------------------------
		// headers
		if (req.headers != null && !req.headers.isEmpty()) {
			if (separateHeaders) {
				sb.append(postfix).append("\t.headers(getHeaders_").append(threeDigits(findHeaderIndex(req))).append("())");
			} else {
				for (Map.Entry<String, String> e : req.headers.entrySet()) {
					sb.append(postfix).append("\t.header(\"").append(escape(e.getKey())).append("\", \"").append(escape(e.getValue())).append("\")");
				}
			}
		}
		
		//----------------------------------
		// params
		if (req.params != null && !req.params.isEmpty()) {
			if (separateParams) {
				sb.append(postfix).append("\t.params(getParams_").append(threeDigits(findParamIndex(req))).append("())");
			} else {
				for (Map.Entry<String, String> e : req.params.entrySet()) {
					sb.append(postfix).append("\t.param(\"").append(escape(e.getKey())).append("\", \"").append(escape(e.getValue())).append("\")");
				}
			}
		}
		//----------------------------------
		// post data/body
		if (req.postData != null && !req.postData.isEmpty()) {
			sb.append(postfix).append("\t.body(\"").append(escape(req.postData)).append("\")");
		}
		
		//----------------------------------
		// options
		if (!cbExcludeRedirects.isSelected()) {		sb.append(postfix).append("\t.disableFollowRedirects()");	}
		if (cbAddCheckStatusEquals.isSelected()) {	sb.append(postfix).append("\t.checkStatusEquals(200)");	}
		if (cbAddCheckBodyContains.isSelected()) {	sb.append(postfix).append("\t.checkBodyContains(\"\")");	}
		if (cbAddMeasureSize.isSelected()) {		sb.append(postfix).append("\t.measureSize(ByteSize.KB)");	}
		
		//----------------------------------
		// send
		sb.append(postfix).append("\t.send()");
		
		//----------------------------------
		// Throw on Fail
		if (cbAddThrowOnFail.isSelected()) {
			sb.append(postfix).append("\t.throwOnFail()");
		} 
		
		//----------------------------------
		// End
		sb.append(postfix).append("\t;");
		
		return sb.toString();
	}

	/*****************************************************************************
	 * Find header method index for a given request. This is used to reference the numbered getHeaders_N method.
	 *
	 * @param req the request
	 * @return index integer
	 *****************************************************************************/
	private int findHeaderIndex(RequestEntry req) {
		// Simple deterministic mapping based on the position in the filtered list
		List<RequestEntry> filtered = filterRequests();
		int count = 0;
		for (RequestEntry r : filtered) {
			if (r.headers != null && !r.headers.isEmpty()) {
				if (r == req) return count;
				count++;
			}
		}
		return Math.max(0, count - 1);
	}

	/*****************************************************************************
	 * Find param method index for a given request.
	 *
	 * @param req request
	 * @return index
	 *****************************************************************************/
	private int findParamIndex(RequestEntry req) {
		List<RequestEntry> filtered = filterRequests();
		int count = 0;
		for (RequestEntry r : filtered) {
			if (r.params != null && !r.params.isEmpty()) {
				if (r == req) return count;
				count++;
			}
		}
		return Math.max(0, count - 1);
	}

	/*****************************************************************************
	 * Filters the loaded HAR requests according to the UI options.
	 *
	 * @return filtered list of HarRequestEntry
	 *****************************************************************************/
	private List<RequestEntry> filterRequests() {
		if (requestModel == null || requestModel.entries == null) return Collections.emptyList();

		// Build regex patterns
		String regexText = tfExcludeRegex.getText();
		List<Pattern> patterns = Arrays.stream(regexText.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(p -> {
					try {
						return Pattern.compile(p);
					} catch (Exception e) {
						// fallback: treat as literal
						return Pattern.compile(Pattern.quote(p));
					}
				})
				.collect(Collectors.toList());

		return requestModel.entries.stream().filter(e -> {
			// resource type filters
			if (cbExcludeCss.isSelected() && "stylesheet".equalsIgnoreCase(e.resourceType)) return false;
			if (cbExcludeScripts.isSelected() && "script".equalsIgnoreCase(e.resourceType)) return false;
			if (cbExcludeImages.isSelected() && e.resourceType.toLowerCase().contains("image")) return false;
			if (cbExcludeFonts.isSelected() && "font".equalsIgnoreCase(e.resourceType)) return false;

			// regex exclude by URL
			for (Pattern p : patterns) {
				try {
					if (p.matcher(e.url).find()) return false;
				} catch (Exception ex) {
					// ignore pattern errors
				}
			}
			return true;
		}).collect(Collectors.toList());
	}

	/*****************************************************************************
	 * Escape string for embedding into generated Java source (simple).
	 *
	 * @param s input string
	 * @return escaped string
	 *****************************************************************************/
	private String escape(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
	}

	/*****************************************************************************
	 * Multiplies the number by 10 and makes it display as 3 digits.
	 *
	 * @param number
	 * @return 3 digits string
	 *****************************************************************************/
	private static String threeDigits(int number) {
		return String.format("%03d", number*10);
	}
	
	/*****************************************************************************
	 * Create a safe Java identifier from a URL (very simple sanitizer).
	 *
	 * @param url url
	 * @return sanitized name
	 *****************************************************************************/
//	private String sanitizeName(int i, String urlQuery) {
//	
//		//-------------------------------
//		// Check Null
//		if (urlQuery == null) return "request";
//		
//		//-------------------------------
//		// Remove slashes
//		urlQuery = urlQuery.substring(1); // Remove slash at the beginning
//		if(urlQuery.endsWith("/")) {
//			urlQuery = urlQuery.substring(0, urlQuery.length()-1); // Remove slash at the end
//		}
//		
//		
//		//-------------------------------
//		// Remove Special chars
//		String cleaned = urlQuery.replaceAll("[^a-zA-Z0-9]", "_");
//		if (cleaned.length() > 40) cleaned = cleaned.substring(0, 40);
//		if (cleaned.isEmpty()) cleaned = "request";
//		
//		if(cleaned.endsWith("_")) {
//			cleaned = cleaned.substring(0, cleaned.length()-1); // Remove _ at the end
//		}
//		
//		//-------------------------------
//		// Prefix with 3 digits
//		return threeDigits(i) + "_" + cleaned;
//	}

	// -------------------------
	// Inner helper classes
	// -------------------------

	/*****************************************************************************
	 * Represents the parsed HAR model (only the pieces we need).
	 *****************************************************************************/
	private static class RequestModel {
		List<RequestEntry> entries;

		RequestModel() {
			this.entries = new ArrayList<>();
		}

		RequestModel(List<RequestEntry> entries) {
			this.entries = entries;
		}
	}

	/*****************************************************************************
	 * Represents a single request extracted from the HAR.
	 *****************************************************************************/
	private static class RequestEntry {
		String method = "GET";
		String url = "";
		String urlHost = "";
		String urlQuery = "";
		String urlVariable = "";
		String sanitizedName = "";
		LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		LinkedHashMap<String, String> params = new LinkedHashMap<>();
		String postData = "";
		String resourceType = "";
		
		public static ArrayList<String> hostURLs = new ArrayList<>();
		
		
		
		/*********************************************
		 * Returns the name prefixed with a 3 digit
		 * index.
		 *********************************************/
		public String indexedName(int index) {
			return PFRHttpConvertHar.threeDigits(index) + "_" + sanitizedName;
		}
			
		
		/*********************************************
		 * Set the URL of this Request Entry.
		 *********************************************/
		public void setURL(String URL) {
			
			this.url = URL;
			this.urlHost = PFR.Text.extractRegexFirst("(.*?//.*?)/", 0, URL);
			this.urlQuery = PFR.Text.extractRegexFirst(".*?//.*?(/.*)", 0, URL);
			setSanitizeName(urlQuery);
			
			if( ! hostURLs.contains(urlHost) ) {
				
			}
		}
		
		/*****************************************************************************
		 * Create a safe Java identifier from a URL.
		 *
		 * @param url url
		 * @return sanitized name
		 *****************************************************************************/
		private void setSanitizeName(String urlQuery) {
		
			//-------------------------------
			// Check Null
			if (urlQuery == null) sanitizedName = "request";
			
			//-------------------------------
			// Remove slashes
			urlQuery = urlQuery.substring(1); // Remove slash at the beginning
			if(urlQuery.endsWith("/")) {
				urlQuery = urlQuery.substring(0, urlQuery.length()-1); // Remove slash at the end
			}
			
			//-------------------------------
			// Remove Special chars
			String cleaned = urlQuery.replaceAll("[^a-zA-Z0-9]", "_");
			if (cleaned.length() > 40) cleaned = cleaned.substring(0, 40);
			if (cleaned.isEmpty()) cleaned = "request";
			
			if(cleaned.endsWith("_")) {
				cleaned = cleaned.substring(0, cleaned.length()-1); // Remove _ at the end
			}
			
			//-------------------------------
			// Prefix with 3 digits
			sanitizedName = cleaned;
		}
	}

	/*****************************************************************************
	 * Small ChangeListener adapter for spinners to call regenerateCode().
	 *****************************************************************************/
	private class ChangeListenerForSpinner implements javax.swing.event.ChangeListener {
		@Override
		public void stateChanged(javax.swing.event.ChangeEvent e) {
			regenerateCode();
		}
	}

}

