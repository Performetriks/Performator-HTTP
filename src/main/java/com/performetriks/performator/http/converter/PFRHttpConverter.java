package com.performetriks.performator.http.converter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
import javax.swing.ScrollPaneConstants;
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

import org.graalvm.polyglot.Value;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.http.PFRHttp;
import com.performetriks.performator.http.scriptengine.PFRScripting;
import com.performetriks.performator.http.scriptengine.PFRScriptingContext;

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
public class PFRHttpConverter extends JFrame {

	private static final long serialVersionUID = 1L;
	
	// ---------------------------
	// UI Elements (left / right)
	// ---------------------------
	private static final String PLACEHOLDER_CLASS_START = "{{class_start}}";
	private static final String PLACEHOLDER_CLASS_END = "{{class_end}}";
	private static final String PLACEHOLDER_INITIALIZE_START = "{{initialize_start}}";
	private static final String PLACEHOLDER_INITIALIZE_END = "{{initialize_end}}";
	private static final String PLACEHOLDER_EXECUTE_START = "{{execute_start}}";
	private static final String PLACEHOLDER_EXECUTE_END = "{{execute_end}}";
	private static final String PLACEHOLDER_TERMINATE_START = "{{terminate_start}}";
	private static final String PLACEHOLDER_CATCH_BLOCK = "{{catch_block}}";
	private static final String PLACEHOLDER_HEADERS_AFTER = "{{headers_after}}";
	private static final String PLACEHOLDER_REQUEST_AFTER = "{{request_after}}";

	// ---------------------------
	// UI Elements (left / right)
	// ---------------------------
	private final JTextPane outputArea = new JTextPane();
	private final JButton btnChooseHar = new JButton("Open HAR...");
	private final JButton btnChoosePostman = new JButton("Open Postman...");
	private final JLabel labelFilePath = new JLabel("No file chosen");

	private final JCheckBox cbExcludeRedirects = new JCheckBox("Exclude Redirects", true);
	private final JCheckBox cbExcludeCss = new JCheckBox("Exclude CSS", true);
	private final JCheckBox cbExcludeScripts = new JCheckBox("Exclude Scripts", true);
	private final JCheckBox cbExcludeImages = new JCheckBox("Exclude Images", true);
	private final JCheckBox cbExcludeFonts = new JCheckBox("Exclude Fonts", true);
	private final JTextField tfExcludeRegex = new JTextField(".*.fileA,.*fileB");

	private final JCheckBox cbMakeURLVariable = new JCheckBox("Make URL Variables", true);
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
	private final JSpinner spMaxNameLength = new JSpinner(new SpinnerNumberModel(40, 0, Integer.MAX_VALUE, 1));

	private final JRadioButton rbSlaGlobal = new JRadioButton("Global", true);
	private final JRadioButton rbSlaPerRequest = new JRadioButton("Per Request", false);
	private final JRadioButton rbSlaNone = new JRadioButton("None", false);
	private final JTextPane postprocessArea = new JTextPane();
	private final JButton btnUpdate = new JButton("Update");

	// Parsed HAR model (in-memory)
	private RequestModel requestModel = new RequestModel();

	private Color reallyDark = new Color(20, 20, 20);
	private Color reallyLight = new Color(230, 230, 230);
	/*****************************************************************************
	 * Main entrypoint: create and show the UI.
	 *
	 * @param args not used
	 *****************************************************************************/
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			PFRHttpConverter g = new PFRHttpConverter();
			g.setVisible(true);
		});
	}

	/*****************************************************************************
	 * Constructor: sets up the JFrame and initializes UI components.
	 *****************************************************************************/
	public PFRHttpConverter() {
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
       
        //--------------------------------------
		// Create left panel with inputs
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BorderLayout());
		leftPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		//--------------------------------------
		// Top: file chooser area
		JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		labelFilePath.setForeground(reallyLight);
		filePanel.add(btnChooseHar);
		filePanel.add(btnChoosePostman);
		filePanel.add(labelFilePath);
		leftPanel.add(filePanel, BorderLayout.NORTH);

		//--------------------------------------
		// Middle: inputs in a scroll pane
		JPanel inputs = new JPanel(new GridBagLayout());
		
		GridBagConstraints grid = new GridBagConstraints();
		grid.insets = new Insets(4, 4, 4, 4);
		grid.anchor = GridBagConstraints.WEST;
		grid.fill = GridBagConstraints.HORIZONTAL;
		grid.weightx = 1.0; // do not grow bigger than parent container
		grid.gridx = 0;
		grid.gridy = 0;

		
		//--------------------------------------
		// Helper to add labeled components with tooltips (decorator)
		BiConsumer<JComponent, String> addWithDesc = (comp, desc) -> {
			comp.setToolTipText("<html>" + desc + "</html>");
			grid.gridx = 0;
			inputs.add(comp instanceof JCheckBox ? comp : new JLabel(comp.getName() == null ? "" : comp.getName()), grid);
		};

		//--------------------------------------
		// We'll add rows manually with label and component
		// Row: Exclude Redirects
		int row = 0;
		addLabeled(inputs, grid,  "Exclude Redirects", cbExcludeRedirects,
				"Checkbox to disable auto following HTTP redirects should be included in the script. " +
						"If unchecked, the generated request builder will include .disableFollowRedirects().", row++);
		addLabeled(inputs, grid,  "Exclude CSS", cbExcludeCss,
				"Do not generate code for requests that have _resourceType=stylesheet.", row++);
		addLabeled(inputs, grid,  "Exclude Scripts", cbExcludeScripts,
				"Do not generate code for requests that have _resourceType=script.", row++);
		addLabeled(inputs, grid,  "Exclude Images", cbExcludeImages,
				"Do not generate code for requests that have image types in _resourceType.", row++);
		addLabeled(inputs, grid,  "Exclude Fonts", cbExcludeFonts,
				"Do not generate code for requests that have _resourceType=font.", row++);

		//--------------------------------------
		// Exclude Regex
		JLabel lblExcludeRegex = new JLabel("Exclude Regex (comma separated):");
		lblExcludeRegex.setToolTipText("Excludes any request whose URL matches any of the provided regular expressions.");
		grid.gridx = 0;
		grid.gridy++;
		inputs.add(lblExcludeRegex, grid);
		grid.gridx = 1;
		tfExcludeRegex.setToolTipText("Comma separated regex list to exclude requests by URL. Default: .*.js,.*.css,.*.svg");
		inputs.add(tfExcludeRegex, grid);
		row++;

		//--------------------------------------
		// Other toggles
		addLabeled(inputs, grid, "Make URL Variables", cbMakeURLVariable,
				"If selected, the host part of the url will be extracted and put into a variable.", row++);
		addLabeled(inputs, grid, "Separate Responses", cbSeparateResponses,
				"If selected, each response gets it's own PFRHttpResponse instance.", row++);
		addLabeled(inputs, grid, "Separate Requests", cbSeparateRequests,
				"If selected, HTTP requests will be generated into separate methods (one per request) and called from execute().", row++);
		addLabeled(inputs, grid, "Separate Headers", cbSeparateHeaders,
				"If selected, headers will be emitted in separate getHeaders*() methods; otherwise they are inline as .header(...).", row++);
		addLabeled(inputs, grid, "Separate Parameters", cbSeparateParameters,
				"If selected, parameters will be emitted in separate getParams*() methods; otherwise they are inline as .param(...).", row++);
		addLabeled(inputs, grid, "Surround with try-catch", cbSurroundTryCatch,
				"If selected, execute() will include try-catch around the requests.", row++);

		//--------------------------------------
		// Checks / placeholders
		addLabeled(inputs, grid, "Add checkBodyContains(\"\")"
				, cbAddCheckBodyContains,
				"Add a placeholder checkBodyContains(\"\") in the generated request builder chain."
				, row++
				);
		
		addLabeled(inputs, grid, "Add checkStatusEquals(200)"
				, cbAddCheckStatusEquals,
				"Add a placeholder checkStatusEquals(200) in the generated request builder chain."
				, row++
				);
		
		addLabeled(inputs, grid, "Add measureSize(ByteSize.KB)"
				, cbAddMeasureSize,
				"Add a placeholder measureSize(ByteSize.KB) in the generated request builder chain."
				, row++
				);
		
		addLabeled(inputs, grid, "Add throwOnFail()"
				, cbAddThrowOnFail,
				"Add throwOnFail() after .send() in the generated chain."
				, row++
				);

		//--------------------------------------
		// Other general options
		addLabeled(inputs, grid, "Debug Log On Fail", cbDebugLogOnFail,
				"If selected, PFRHttp.debugLogFail(true); will be emitted in initializeUser().", row++);

		//--------------------------------------
		// Numeric spinners
		JLabel lblRespTO = new JLabel("Default Response Timeout (s) [0 = none]:");
		lblRespTO.setToolTipText("If > 0 then PFRHttp.defaultResponseTimeout(Duration.ofSeconds(value)) will be added.");
		grid.gridx = 0;
		grid.gridy++;
		inputs.add(lblRespTO, grid);
		grid.gridx = 1;
		inputs.add(spDefaultResponseTimeout, grid);

		JLabel lblPause = new JLabel("Default Pause (ms) [0 = none]:");
		lblPause.setToolTipText("If > 0 then PFRHttp.defaultPause(value) will be added.");
		grid.gridx = 0;
		grid.gridy++;
		inputs.add(lblPause, grid);
		grid.gridx = 1;
		inputs.add(spDefaultPause, grid);
		
		JLabel lblMaxNameLength = new JLabel("Max Name length:");
		lblPause.setToolTipText("Defines the maximum length of the request name.");
		grid.gridx = 0;
		grid.gridy++;
		inputs.add(lblMaxNameLength, grid);
		grid.gridx = 1;
		inputs.add(spMaxNameLength, grid);

		//--------------------------------------
		// SLA radio buttons
		JLabel lblSla = new JLabel("Default SLA:");
		lblSla.setToolTipText("Global: add a DEFAULT_SLA constant. Per Request: add a .sla(...) per request. None: do nothing.");
		grid.gridx = 0;
		grid.gridy++;
		inputs.add(lblSla, grid);
		grid.gridx = 1;
		JPanel pSla = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		ButtonGroup bgSla = new ButtonGroup();
		bgSla.add(rbSlaGlobal);
		bgSla.add(rbSlaPerRequest);
		bgSla.add(rbSlaNone);
		pSla.add(rbSlaGlobal);
		pSla.add(rbSlaPerRequest);
		pSla.add(rbSlaNone);
		inputs.add(pSla, grid);
		
		//------------------------------------
		// Place inputs into scroll pane
		JScrollPane scrollInputs = new JScrollPane(inputs);
		scrollInputs.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		leftPanel.add(scrollInputs, BorderLayout.CENTER);
		
		
		//########################################################################################
		// Add Scripting Section
		//########################################################################################
		
		//------------------------------------
		// Add PostProcess
		JLabel lblpostprocessArea = new JLabel("Javascript Post Process:");
		lblpostprocessArea.setToolTipText("Used to adjust the generated code to your liking using javascript.");

		prepareTextarea(postprocessArea);
		postprocessArea.setText("""
				function postProcess(code){
					//code = code.replaceAll('%s', '') // insert at class start
					//code = code.replaceAll('%s', '') // insert at class end
					//code = code.replaceAll('%s', '') // insert at start of method initializeUser()
					//code = code.replaceAll('%s', '') // insert at end of method initializeUser()
					//code = code.replaceAll('%s', '') // insert at start of method execute()
					//code = code.replaceAll('%s', '') // insert at end of method execute()
					//code = code.replaceAll('%s', '') // insert at start of method terminate()
					//code = code.replaceAll('%s', '') // insert in the catch block
					//code = code.replaceAll('%s', 'headers.put("maHeader", "zeHeader");') // insert after headers
					//code = code.replaceAll('%s', '') // insert after every request
					return code;
				}""".formatted(
						  PLACEHOLDER_CLASS_START
						, PLACEHOLDER_CLASS_END
						, PLACEHOLDER_INITIALIZE_START
						, PLACEHOLDER_INITIALIZE_END
						, PLACEHOLDER_EXECUTE_START
						, PLACEHOLDER_EXECUTE_END
						, PLACEHOLDER_TERMINATE_START
						, PLACEHOLDER_CATCH_BLOCK
						, PLACEHOLDER_HEADERS_AFTER
						, PLACEHOLDER_REQUEST_AFTER
					)
				);
		
		

		JScrollPane scrollPostProcess = new JScrollPane(postprocessArea);
		scrollPostProcess.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);


		JPanel scriptingPanel = new JPanel(new BorderLayout());
		scriptingPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		scriptingPanel.add(lblpostprocessArea,BorderLayout.NORTH);
		scriptingPanel.add(scrollPostProcess, BorderLayout.CENTER);
		scriptingPanel.add(btnUpdate, BorderLayout.SOUTH);
		
		leftPanel.add(scriptingPanel, BorderLayout.SOUTH);
		
		//------------------------------------
		// Add Update Button
		//JLabel lblscripting = new JLabel("");

		//btnUpdate.setToolTipText("Used to adjust the generated code to your liking using javascript .");
//		grid.gridx = 0;
//		grid.gridy++;
//		inputs.add(lblButtonUpdate, grid);
//		grid.gridx = 1;
//		inputs.add(btnUpdate, grid);
		
		//########################################################################################
		// Add Output Section
		//########################################################################################
		
		//------------------------------------
		// Configure output area

		//outputArea.setLineWrap(false);
		//outputArea.setWrapStyleWord(false);
		//outputArea.setTabSize(4);
		
		prepareTextarea(outputArea);
		
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
	 * Sets some default values for text areas used for code.
	 *
	 * @param textarea
	 *****************************************************************************/
	private void prepareTextarea(JTextPane textarea) {
		textarea.setBackground(reallyDark);    
		textarea.setForeground(reallyLight);   
		textarea.setCaretColor(reallyLight);   
		
		textarea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		
		FontMetrics fm = textarea.getFontMetrics(textarea.getFont());
		int charWidth = fm.charWidth(' ');
		int tabWidth = charWidth * 4; // 4-space tab

		TabStop[] stops = new TabStop[20];
		for (int i = 0; i < stops.length; i++) {
		    stops[i] = new TabStop((i + 1) * tabWidth);
		}
		TabSet tabSet = new TabSet(stops);

		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setTabSet(attrs, tabSet);

		StyledDocument doc = textarea.getStyledDocument();
		doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
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
		cbMakeURLVariable.addItemListener(itemListener);
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
		spMaxNameLength.addChangeListener(changeListenerForSpinner);

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
		btnChoosePostman.addActionListener(e -> choosePostmanFile());
		btnUpdate.addActionListener(e -> regenerateCode());
		
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
		chooser.setFileFilter(new FileNameExtensionFilter("HTTP Archive (.har)", "har"));
		int res = chooser.showOpenDialog(this);
		if (res == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			labelFilePath.setText(f.getAbsolutePath());
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
	 * Handles HAR file selection and parsing.
	 *****************************************************************************/
	private void choosePostmanFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("Postman Collection (.json)", "json"));
		int res = chooser.showOpenDialog(this);
		
		if (res == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			labelFilePath.setText(f.getAbsolutePath());
			try {
				parsePostmanCollection(f);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this, "Failed to parse Postman Collection: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
		
		RequestEntry.clearHostList();
		
		try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			
			//---------------------------------
			// Get Log Element
			JsonElement rootEl = JsonParser.parseReader(r);
			JsonObject root = rootEl.isJsonObject() ? rootEl.getAsJsonObject() : new JsonObject();
			JsonObject log = root.has("log") && root.get("log").isJsonObject() ? root.getAsJsonObject("log") : null;
			
			//---------------------------------
			// Get Entries Array
			List<RequestEntry> entries = new ArrayList<>();
			if (log != null && log.has("entries") && log.get("entries").isJsonArray()) {
				JsonArray arr = log.getAsJsonArray("entries");
				
				//---------------------------------
				// Iterate Entries
				for (JsonElement el : arr) {
					if (!el.isJsonObject()) continue;
					JsonObject entry = el.getAsJsonObject();
					JsonObject req = entry.has("request") && entry.get("request").isJsonObject() ? entry.getAsJsonObject("request") : null;
					JsonObject resp = entry.has("response") && entry.get("response").isJsonObject() ? entry.getAsJsonObject("response") : null;
					
					if (req == null) continue;

					RequestEntry hre = new RequestEntry();
					hre.method = req.has("method") ? req.get("method").getAsString() : "GET";
					hre.setURL( req.has("url") ? req.get("url").getAsString() : "");
					
					//--------------------------------------
					// Get Status
					if ( resp != null 
					  && resp.has("status") 
					  && resp.get("status").isJsonPrimitive()
					  ){
						hre.status = resp.get("status").getAsInt();
					}
					
					//--------------------------------------
					// Get Headers
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
					// Get Post Data
					if (req.has("postData") && req.get("postData").isJsonObject()) {
						JsonObject pd = req.getAsJsonObject("postData");
						if (pd.has("text")) {
							hre.body = pd.get("text").getAsString();
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
								value = PFRHttp.decode(value);
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
	 * Parse a Postman Collection JSON file into an in-memory RequestModel.
	 *
	 * This loads JSON with GSON and extracts:
	 * - request name
	 * - request url
	 * - request method
	 * - headers
	 * - body (raw if available)
	 * - URL query parameters
	 *
	 * Postman collections are recursive: any object may contain an "item" array.
	 *
	 * @param file Postman Collection JSON to parse
	 * @throws IOException on IO problems
	 *****************************************************************************/
	private void parsePostmanCollection(File file) throws IOException {

	    RequestEntry.clearHostList();

	    try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
	        JsonElement rootEl = JsonParser.parseReader(r);
	        JsonObject root = rootEl.isJsonObject() ? rootEl.getAsJsonObject() : new JsonObject();

	        List<RequestEntry> entries = new ArrayList<>();

	        // top-level "item" array
	        if (root.has("item") && root.get("item").isJsonArray()) {
	            JsonArray arr = root.getAsJsonArray("item");
	            parseItemArray(arr, entries);
	        }

	        requestModel = new RequestModel(entries);
	    }
	}
	
	/*****************************************************************************
	 * Recursively process any "item" array in a Postman collection.
	 *****************************************************************************/
	private void parseItemArray(JsonArray items, List<RequestEntry> entries) {

	    for (JsonElement el : items) {
	        if (!el.isJsonObject()) continue;
	        JsonObject itemObj = el.getAsJsonObject();

	        // If this object contains a request, parse it
	        if (itemObj.has("request") && itemObj.get("request").isJsonObject()) {
	            JsonObject req = itemObj.getAsJsonObject("request");

	            RequestEntry hre = new RequestEntry();

	            // -------------------------
	            // Name
	            hre.name = itemObj.has("name") ? itemObj.get("name").getAsString() : "";

	            // -------------------------
	            // Method
	            hre.method = req.has("method") ? req.get("method").getAsString() : "GET";

	            // -------------------------
	            // URL
	            String url = extractPostmanURL(req);
	            hre.setURL(url);

	            // -------------------------
	            // Headers
	            hre.headers = new LinkedHashMap<>();
	            if (req.has("header") && req.get("header").isJsonArray()) {
	                JsonArray hdrs = req.getAsJsonArray("header");
	                for (JsonElement h : hdrs) {
	                    if (!h.isJsonObject()) continue;
	                    JsonObject ho = h.getAsJsonObject();
	                    String name = ho.has("key") ? ho.get("key").getAsString() : "";
	                    String value = ho.has("value") ? ho.get("value").getAsString() : "";
	                    if (!name.isEmpty()) hre.headers.put(name, value);
	                }
	            }

	            // -------------------------
	            // Body
	            if (req.has("body") && req.get("body").isJsonObject()) {
	                JsonObject body = req.getAsJsonObject("body");

	                // raw body
	                if (body.has("raw")) {
	                    hre.body = body.get("raw").getAsString();
	                }
	            }

	            // -------------------------
	            // Query parameters attached to URL
	            extractPostmanParamsToEntry(req, hre);

	            entries.add(hre);
	        }

	        // -------------------------
	        // Recurse deeper if nested "item" exists
	        if (itemObj.has("item") && itemObj.get("item").isJsonArray()) {
	            parseItemArray(itemObj.getAsJsonArray("item"), entries);
	        }
	    }
	}

	/*****************************************************************************
	 *
	 *****************************************************************************/
	private String extractPostmanURL(JsonObject postmanItem) {

	    if (!postmanItem.has("url")) return "";

	    JsonElement uel = postmanItem.get("url");

	    // "url" may be a string OR an object
	    if (uel.isJsonPrimitive()) {
	        return uel.getAsString();
	    }

	    JsonObject urlObj = uel.getAsJsonObject();

	    StringBuilder sb = new StringBuilder();

	    // protocol://
	    if (urlObj.has("protocol"))
	        sb.append(urlObj.get("protocol").getAsString()).append("://");

	    // host segments
	    if (urlObj.has("host") && urlObj.get("host").isJsonArray()) {
	        JsonArray hostArr = urlObj.getAsJsonArray("host");
	        for (int i = 0; i < hostArr.size(); i++) {
	            if (i > 0) sb.append(".");
	            sb.append(hostArr.get(i).getAsString());
	        }
	    }

	    // path segments
	    if (urlObj.has("path") && urlObj.get("path").isJsonArray()) {
	        for (JsonElement p : urlObj.getAsJsonArray("path")) {
	            sb.append("/").append(p.getAsString());
	        }
	    }

	    // query parameters (append later)
	    return sb.toString();
	}

	/*****************************************************************************
	 *
	 *****************************************************************************/
	private void extractPostmanParamsToEntry(JsonObject postmanItem, RequestEntry req) {

	    req.params = new LinkedHashMap<>();

	    if (!postmanItem.has("url")) return;

	    JsonElement uel = postmanItem.get("url");

	    if (!uel.isJsonObject()) return;

	    JsonObject urlObj = uel.getAsJsonObject();

	    if (urlObj.has("query") && urlObj.get("query").isJsonArray()) {
	        JsonArray qarr = urlObj.getAsJsonArray("query");
	        for (JsonElement q : qarr) {
	            if (!q.isJsonObject()) continue;

	            JsonObject qo = q.getAsJsonObject();
	            String key = qo.has("key") ? qo.get("key").getAsString() : null;
	            String val = qo.has("value") ? qo.get("value").getAsString() : "";

	            if (key != null) req.params.put(key, val);
	        }
	    }
	}



	/*****************************************************************************
	 * Regenerate the code in the output textarea based on current inputs and the loaded HAR model.
	 *
	 * This method is called whenever any input is changed (or the HAR is loaded).
	 *****************************************************************************/
	private void regenerateCode() {
		int currentPos = outputArea.getCaretPosition();
		
		String code = generateFullClassCode();
		code = postProcessScript(code);
		outputArea.setText(code);
		outputArea.setCaretPosition(Math.min(currentPos, code.length()-1));
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
		
		//--------------------------------------------------
		// Package & Imports
		sb.append("""
package com.performetriks.performator.quickstart.usecase;

import java.util.LinkedHashMap;
import com.performetriks.performator.base.PFRUsecase;
import com.performetriks.performator.http.PFRHttp;
import com.performetriks.performator.http.PFRHttpResponse;
import com.performetriks.performator.http.ResponseFailedException;
import com.xresch.hsr.stats.HSRExpression.Operator;
import com.xresch.hsr.stats.HSRRecordStats.HSRMetric;
import com.xresch.hsr.stats.HSRSLA;

public class UsecaseConverted extends PFRUsecase {
				
			""");
		
		sb.append("\t"+PLACEHOLDER_CLASS_START+"\n");
		
		//--------------------------------------------------
		// Class header & optional global SLA
		boolean slaGlobal = rbSlaGlobal.isSelected();
		if (slaGlobal) {
			
			sb.append("\n\tprivate HSRSLA DEFAULT_SLA = new HSRSLA(HSRMetric.failrate, Operator.LT, 5);\n\n");
		}
		
		
		
		//--------------------------------------------------
		// URL Variables
		if(cbMakeURLVariable.isSelected()) {
			ArrayList<String> hostList = RequestEntry.getHostList();
			for(int i = 0; i < hostList.size(); i++) {
				sb.append("\tprivate String url_"+i+" = \""+hostList.get(i)+"\";\n");
			}
		}
		
		//--------------------------------------------------
		// initializeUser method
		sb.append("	/***************************************************************************\n");
		sb.append("	 * initializeUser\n");
		sb.append("	 ***************************************************************************/\n");
		sb.append("	@Override\n");
		sb.append("	public void initializeUser() {\n");
		sb.append("		"+PLACEHOLDER_INITIALIZE_START+"\n");
		if ((Integer) spDefaultResponseTimeout.getValue() > 0) {
			sb.append("		PFRHttp.defaultResponseTimeout(Duration.ofSeconds(").append(spDefaultResponseTimeout.getValue()).append("));\n");
		}
		if ((Integer) spDefaultPause.getValue() > 0) {
			sb.append("		PFRHttp.defaultPause(").append(spDefaultPause.getValue()).append(");\n");
		}
		if (cbDebugLogOnFail.isSelected()) {
			sb.append("		PFRHttp.debugLogFail(true);\n");
		}
		sb.append("		"+PLACEHOLDER_INITIALIZE_END+"\n");
		sb.append("	}\n\n");

		// execute signature
		sb.append("	/***************************************************************************\n");
		sb.append("	 * execute\n");
		sb.append("	 ***************************************************************************/\n");
		sb.append("	@Override\n");
		sb.append("	public void execute() throws Throwable {\n");
		sb.append("		"+PLACEHOLDER_EXECUTE_START+"\n");
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
		
		//RequestEntry previous = null;
		for (RequestEntry req : requests) {
			
			String responseVar = "r";

			String name = req.indexedName(idx);
						
			//----------------------------------------
			// Header
			sb.append(postfix).append("//---------------------------------------------");
			sb.append(postfix).append("// ");
			sb.append(postfix).append("//---------------------------------------------");
			
			//----------------------------------------
			// Check Skip Redirects
			// this won't work as we don't know if the order in the HAR file is correct
//			if (cbExcludeRedirects.isSelected() 
//			&& previous != null
//			&& (   previous.status == 301	// Moved
//				|| previous.status == 302	// Found
//				|| previous.status == 307	// Temporary Redirect
//				|| previous.status == 308	// Permanent Redirect
//			    )
//			){		
//				sb.append(postfix).append("// HTTP "+previous.status+" - Redirect Excluded: "+req.url+"\n");
//				previous = req;
//				continue;
//			}
//			
//			previous = req;
			
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
			//sb.append("\r\n");
			//sb.append(postfix).append("// if (!").append(responseVar).append(".isSuccess()) { return; }\r\n");

			idx++;
		}

		if (surroundTry) {
			sb.append("		} catch(ResponseFailedException e) {\n");
			sb.append("			"+PLACEHOLDER_CATCH_BLOCK+"\n");
			sb.append("			throw e;\n");
			sb.append("		}\n");
		}
		sb.append("		"+PLACEHOLDER_EXECUTE_END+"\n");
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
					sb.append("		"+PLACEHOLDER_HEADERS_AFTER+"\n");
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
		sb.append("		"+PLACEHOLDER_TERMINATE_START+"\n");
		sb.append("	}\n\n");
		sb.append("	"+PLACEHOLDER_CLASS_END+"\n");
		sb.append("\n}\n");

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
		
		//--------------------------------------------------
		// URL Variables
		String urlPart = "\""+escape(req.url)+"\"";
		if(cbMakeURLVariable.isSelected()) {
			
			urlPart = req.urlVariable +" + \""+ escape(req.urlQuery) +"\"";

		}

		//--------------------------------------------------
		// Create
		sb.append("PFRHttp.create(\"").append(req.indexedName(idx)).append("\", ").append(urlPart).append(")");
		
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
		if (req.hasBody()) {
			if(req.isBodyJSON()) {
				sb.append(postfix)
					.append("\t.body(\"\"\"")
					.append(postfix).append("\t\t")
						.append( PFR.Text.replaceNewlines(req.bodyAsJson.trim(), postfix+"\t\t") )
					.append(postfix).append("\t\"\"\")")
					;
			}else {
				
				sb.append(postfix)
				.append("\t.body(\"\"\"")
				.append(postfix).append("\t\t")
					.append( PFR.Text.replaceNewlines(req.body.trim(), postfix+"\t\t") )
				.append(postfix).append("\t\"\"\")")
				;
			}
		}
		
		//----------------------------------
		// Various Options
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
		sb.append(postfix).append(PLACEHOLDER_REQUEST_AFTER+"\n");
		
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
		
		int count = 0;
		for (RequestEntry r : requestModel.entries) {
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
	private ArrayList<RequestEntry> filterRequests() {
		
		ArrayList<RequestEntry> filtered = new ArrayList<>();
		
		//------------------------
		// Check Null
		if (requestModel == null || requestModel.entries == null) {
			return filtered;
		}
		
		//------------------------
		// Make List
		for(RequestEntry req : requestModel.entries) {
			if(req.checkIncludeRequest()) {
				filtered.add(req);
			}
		}
		
		return filtered;
	}
	

	/******************************************************************************************************
	 * 
	 ******************************************************************************************************/
	private String postProcessScript(String code) {
		
		String processedCode = code;
		//------------------------------------
		// Execute Javascript
		String customJS = postprocessArea.getText();
		if(customJS != null && customJS.contains("postProcess")) {

			PFRScriptingContext jsEngine = PFRScripting.createJavascriptContext();
			
			try {
				jsEngine.addScript("postprocess.js", postprocessArea.getText());
				Value result = jsEngine.executeScript("postProcess(`"+processedCode+"`);");
				
				if(result != null) {
					
					String resultingCode = result.asString();
					
					if( ! Strings.isNullOrEmpty(resultingCode) ){
						processedCode = resultingCode;
					}
				}
			} catch (Throwable e) {
				// return javascript errors
				return "Javascript Error occured: \r\n"+e.getMessage();
			}
		}
		
		//------------------------------------
		// Remove Leftover placeholders
		processedCode = 
				processedCode.replace(PLACEHOLDER_CLASS_START, "") 
					.replace( PLACEHOLDER_CLASS_END, "") 
					.replace(PLACEHOLDER_INITIALIZE_START, "") 
					.replace(PLACEHOLDER_INITIALIZE_END, "") 
					.replace(PLACEHOLDER_EXECUTE_START, "") 
					.replace(PLACEHOLDER_EXECUTE_END, "") 
					.replace(PLACEHOLDER_TERMINATE_START, "") 
					.replace(PLACEHOLDER_CATCH_BLOCK, "") 
					.replace(PLACEHOLDER_HEADERS_AFTER, "") 
					.replace(PLACEHOLDER_REQUEST_AFTER, "") 
					;
		
		return processedCode;
		
	}
	/*****************************************************************************
	 * Escape string for embedding into generated Java source (simple).
	 *
	 * @param s input string
	 * @return escaped string
	 *****************************************************************************/
	private String escape(String s) {
		
		if (s == null) return "";
		
		return s.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\"", "\\\"")
				.replace("\\", "\\\\")  // we also do double escaping of above, as we run this through the javascript engine
				;
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
	private class RequestEntry {
		
		String method = "GET";
		String name = "";
		
		String url = "";
		String urlHost = "";
		String urlQuery = "";
		String urlVariable = "";
		
		int status = 200; // response status
		
		LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		LinkedHashMap<String, String> params = new LinkedHashMap<>();
		String body = "";
		String bodyAsJson = ""; // used for JSON body
		String resourceType = "";
		
		// keep track of all hosts
		private static ArrayList<String> hostList = new ArrayList<>();
		

		/*********************************************
		 * Returns the name prefixed with a 3 digit
		 * index.
		 *********************************************/
		public String indexedName(int index) {
			return PFRHttpConverter.threeDigits(index) + "_" + getSanitizedName();
		}
			
		/*********************************************
		 * Returns a clone of the host List
		 *********************************************/
		public static ArrayList<String> getHostList() {
			ArrayList<String> clone = new ArrayList<>();
			clone.addAll(hostList);
			return clone;
		}
		
		/*********************************************
		 * Returns a clone of the host List
		 *********************************************/
		public static void clearHostList() {
			 hostList = new ArrayList<>();
		}
		
		/*********************************************
		 * Returns true if request has a body
		 *********************************************/
		public boolean hasBody() {
			return ( body != null && !body.isBlank());
		}
		
		/*********************************************
		 * Returns true if the request body is of 
		 * type JSON
		 *********************************************/
		public boolean isBodyJSON() {
			
			//--------------------------
			// Check no body
			if(!hasBody()) { return false; }
			
			//--------------------------
			// Check JSON start
			if(! body.startsWith("{")
			&& ! body.startsWith("[")) {
				return false;
			}
			
			//--------------------------
			// Check Null
			try {
				JsonElement e = PFR.JSON.fromJson(body);
				bodyAsJson = PFR.JSON.toJSONPretty(e);
			}catch(Throwable e){
				return false;
			}
			
			return true;
		}
		
		/*********************************************
		 * Set the URL of this Request Entry.
		 *********************************************/
		public void setURL(String URL) {
			
			this.url = URL;
			this.urlHost = PFR.Text.extractRegexFirst("(.*?//.*?)/", 0, URL);
			this.urlQuery = PFR.Text.extractRegexFirst(".*?//.*?(/.*)", 0, URL);
			
			//------------------------
			// Define URL Variable
			if( ! hostList.contains(urlHost) ) {
				hostList.add(urlHost);
			}
			
			urlVariable = "url_" + hostList.indexOf(urlHost); 

		}
		
		/*****************************************************************************
		 * Check if the requests should be included according to the UI options.
		 *
		 * @return filtered list of HarRequestEntry
		 *****************************************************************************/
		public boolean checkIncludeRequest() {
			
			//------------------------
			// Check Resource Type
			if (cbExcludeCss.isSelected() && "stylesheet".equalsIgnoreCase(resourceType)) return false;
			if (cbExcludeScripts.isSelected() && "script".equalsIgnoreCase(resourceType)) return false;
			if (cbExcludeImages.isSelected() && resourceType.toLowerCase().contains("image")) return false;
			if (cbExcludeFonts.isSelected() && "font".equalsIgnoreCase(resourceType)) return false;

			//------------------------
			// Check the regular expressions
			String regexText = tfExcludeRegex.getText();
			
			for(String regex : regexText.split(",")) {
				if(PFR.Text.getRegexMatcherCached(regex.trim(), url).matches()) {
					return false;
				}
			}
			
			
			return true;

		}
		
		/*****************************************************************************
		 * Create a safe Java identifier from a URL.
		 *
		 * @param url url
		 * @return sanitized name
		 *****************************************************************************/
		public String getSanitizedName() {
		
			//-------------------------------
			// Check Null
			if (urlQuery == null) return "request";
			
			//-------------------------------
			// Remove slashes
			String sanitized = ( name != null && !name.isBlank() ) 
									? name 
									: urlQuery.substring(1) // Remove slash at the beginning
									;
			
			if(sanitized.endsWith("/")) {
				sanitized = sanitized.substring(0, sanitized.length()-1); // Remove slash at the end
			}
			
			
			//-------------------------------
			// Remove Special chars
			sanitized = PFRHttp.decode(sanitized);
			int maxLength = (Integer)spMaxNameLength.getValue();
			sanitized = sanitized.replaceAll("[^a-zA-Z0-9]", "_");
			
			if (sanitized.length() > maxLength) sanitized = sanitized.substring(0, maxLength);
			if (sanitized.isEmpty()) sanitized = "request";
			
			if(sanitized.endsWith("_")) {
				sanitized = sanitized.substring(0, sanitized.length()-1); // Remove _ at the end
			}
			
			//-------------------------------
			// Remove consecutive underscores
			return PFR.Text.replaceAll(sanitized, "__+", "_");
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

