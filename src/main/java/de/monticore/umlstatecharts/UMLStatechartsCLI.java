/* (c) https://github.com/MontiCore/monticore */
package de.monticore.umlstatecharts;

import com.google.common.collect.Lists;
import de.monticore.cd4code.prettyprint.CD4CodeFullPrettyPrinter;
import de.monticore.cdbasis._ast.ASTCDClass;
import de.monticore.class2mc.Class2MCResolver;
import de.monticore.generating.GeneratorEngine;
import de.monticore.generating.GeneratorSetup;
import de.monticore.generating.templateengine.GlobalExtensionManagement;
import de.monticore.generating.templateengine.TemplateController;
import de.monticore.generating.templateengine.TemplateHookPoint;
import de.monticore.io.paths.MCPath;
import de.monticore.prettyprint.IndentPrinter;
import de.monticore.prettyprint.UMLStatechartsFullPrettyPrinter;
import de.monticore.sc2cd.HookPointService;
import de.monticore.sc2cd.SC2CDConverter;
import de.monticore.sc2cd.SC2CDData;
import de.monticore.sc2cd.SCTopDecorator;
import de.monticore.scbasis.BranchingDegreeCalculator;
import de.monticore.scbasis.InitialStateCollector;
import de.monticore.scbasis.ReachableStateCollector;
import de.monticore.scbasis.StateCollector;
import de.monticore.scbasis._ast.ASTSCArtifact;
import de.monticore.scbasis._ast.ASTSCState;
import de.monticore.scbasis._cocos.*;
import de.monticore.scbasis._symboltable.SCStateSymbol;
import de.monticore.scevents._cocos.NonCapitalEventNames;
import de.monticore.scevents._cocos.NonCapitalParamNames;
import de.monticore.scevents._symboltable.SCEventsSTCompleter;
import de.monticore.scstatehierarchy.HierarchicalStateCollector;
import de.monticore.scstatehierarchy.NoSubstatesHandler;
import de.monticore.symbols.basicsymbols.BasicSymbolsMill;
import de.monticore.symbols.oosymbols.OOSymbolsMill;
import de.monticore.tf.odrules._ast.ASTODRule;
import de.monticore.tr.UMLStatechartsTFGenCLI;
import de.monticore.tr.umlstatechartstr.UMLStatechartsTRMill;
import de.monticore.tr.umlstatechartstr._ast.ASTUMLStatechartsTFRule;
import de.monticore.types.DeriveSymTypeOfUMLStatecharts;
import de.monticore.types.SynthesizeSymType;
import de.monticore.types.check.TypeCheck;
import de.monticore.umlstatecharts._cocos.UMLStatechartsCoCoChecker;
import de.monticore.umlstatecharts._symboltable.IUMLStatechartsArtifactScope;
import de.monticore.umlstatecharts._symboltable.UMLStatechartsScopesGenitorDelegator;
import de.monticore.umlstatecharts._visitor.UMLStatechartsTraverser;
import de.se_rwth.commons.logging.Log;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UMLStatechartsCLI extends UMLStatechartsCLITOP {

  @Override
  public void run(String[] args){
    Options options = initOptions();

    try {
      // create CLI parser and parse input options from command line
      CommandLineParser cliparser = new DefaultParser();
      CommandLine cmd = cliparser.parse(options, args);

      // help: when --help
      if (cmd.hasOption("h")) {
        printHelp(options);
        // do not continue, when help is printed
        return;
      }

      // if -i input is missing: also print help and stop
      if (!(cmd.hasOption("i") || cmd.hasOption("tg"))) {
        printHelp(options);
        // do not continue, when help is printed
        return;
      }

      if(cmd.hasOption("i")) {
        // we need the global scope for symbols and cocos
        MCPath symbolPath = new MCPath(Paths.get(""));
        if (cmd.hasOption("path")) {
          symbolPath = new MCPath(Arrays.stream(cmd.getOptionValues("path")).map(x -> Paths.get(x))
              .collect(Collectors.toList()));
        }
        UMLStatechartsMill.globalScope().setSymbolPath(symbolPath);
        BasicSymbolsMill.initializePrimitives();
        Class2MCResolver resolver = new Class2MCResolver();
        OOSymbolsMill.globalScope().addAdaptedOOTypeSymbolResolver(resolver);
        OOSymbolsMill.globalScope().addAdaptedTypeSymbolResolver(resolver);
  
        // parse input file, which is now available
        // (only returns if successful)
        ASTSCArtifact scartifact = parse(cmd.getOptionValue("i"));
  
        IUMLStatechartsArtifactScope scope = createSymbolTable(scartifact);
  
        // check context conditions
        runDefaultCoCos(scartifact);
  
        if (cmd.hasOption("s")) {
          String path = cmd.getOptionValue("s", StringUtils.EMPTY);
          storeSymbols(scope, path);
        }
  
        // -option pretty print
        if (cmd.hasOption("pp")) {
          String path = cmd.getOptionValue("pp", StringUtils.EMPTY);
          prettyPrint(scartifact, path);
        }
  
        // -option reports
        if (cmd.hasOption("r")) {
          String path = cmd.getOptionValue("r", StringUtils.EMPTY);
          report(scartifact, path);
        }
  
        // -option generate to CD
        if (cmd.hasOption("gen")) {
          String path = cmd.getOptionValue("gen", StringUtils.EMPTY);
          String configTemplate = cmd.getOptionValue("ct", StringUtils.EMPTY);
          String templatePath = cmd.getOptionValue("fp", StringUtils.EMPTY);
          String handcodedPath = cmd.getOptionValue("hcp", StringUtils.EMPTY);
    
          generateCD(scartifact, path, configTemplate, templatePath, handcodedPath);
        }
      }
  
      if(cmd.hasOption("tg")) {
        UMLStatechartsTRMill.init();
        UMLStatechartsTFGenCLI tfgen = new UMLStatechartsTFGenCLI();
  
        Path model = Paths.get(cmd.getOptionValue("tg"));
  
        // Parse rule
        ASTUMLStatechartsTFRule rule = tfgen.parseRule(model).get();
  
        // check CoCos
        tfgen.checkCoCos(rule);
  
        ASTODRule odrule;
  
        //create od rule
        if (cmd.hasOption("mp")) {
          odrule = tfgen.createODRule(rule, new MCPath(cmd.getOptionValue("mp")));
        } else {
          odrule = tfgen.createODRule(rule, new MCPath());
        }
        // generate
        tfgen.generate(odrule, Paths.get(cmd.getOptionValue("o", "out")).toFile());
      }


    } catch (ParseException e) {
      // ann unexpected error from the apache CLI parser:
      Log.error("0xA5C01 Could not process CLI parameters: " + e.getMessage());
    }
  }

  /**
   * Creates the symbol table from the parsed AST.
   *
   * @param ast The top statechart model element.
   * @return The artifact scope derived from the parsed AST
   */
  @Override
  public IUMLStatechartsArtifactScope createSymbolTable(ASTSCArtifact ast) {

    // create scope and symbol skeleton
    UMLStatechartsScopesGenitorDelegator genitor = UMLStatechartsMill.scopesGenitorDelegator();
    IUMLStatechartsArtifactScope symTab = genitor.createFromAST(ast);

    // complete symbols including type check
    UMLStatechartsTraverser completer = UMLStatechartsMill.traverser();
    TypeCheck typeCheck = new TypeCheck(new SynthesizeSymType(),new DeriveSymTypeOfUMLStatecharts());
    completer.add4SCEvents(new SCEventsSTCompleter(typeCheck));
    ast.accept(completer);

    return symTab;
  }

  /**
   * Creates reports for the Statechart-AST to stdout or a specified file.
   *
   * @param scartifact The Statechart-AST for which the reports are created
   * @param path The target path of the directory for the report artifacts. If
   *          empty, the contents are printed to stdout instead
   */
  @Override
  public void report(ASTSCArtifact scartifact, String path) {
    // calculate and print reports
    String reachable = reportReachableStates(scartifact);
    print(reachable, path, REPORT_REACHABILITY);

    String branching = reportBranchingDegree(scartifact);
    print(branching, path, REPORT_BRANCHING_DEGREE);

    String stateNames = reportStateNames(scartifact);
    print(stateNames, path, REPORT_STATE_NAMES);
  }

  // names of the reports:
  public static final String REPORT_REACHABILITY = "reachability.txt";
  public static final String REPORT_BRANCHING_DEGREE = "branchingDegree.txt";
  public static final String REPORT_STATE_NAMES = "stateNames.txt";

  public String reportReachableStates(ASTSCArtifact ast) {
    UMLStatechartsTraverser traverser = UMLStatechartsMill.traverser();
    // collect all states
    // HierarchicalStateCollector vs StateCollector
    HierarchicalStateCollector stateCollector = new HierarchicalStateCollector();
    traverser.add4SCBasis(stateCollector);
    traverser.add4SCStateHierarchy(stateCollector);
    ast.accept(traverser);
    Set<String> statesToBeChecked = stateCollector.getStates()
      .stream().map(e -> e.getName()).collect(Collectors.toSet());

    // collect all initial states
    traverser = UMLStatechartsMill.traverser();
    InitialStateCollector initialStateCollector = new InitialStateCollector();
    traverser.add4SCBasis(initialStateCollector);
    //  only find real initial states
    traverser.setSCStateHierarchyHandler(new NoSubstatesHandler());
    ast.accept(traverser);
    Set<String> reachableStates = initialStateCollector.getStates();

    // calculate reachable states
    Set<String> currentlyChecked = new HashSet<>(reachableStates);
    statesToBeChecked.removeAll(reachableStates);
    while (!currentlyChecked.isEmpty()) {
      // While the open list is not empty, check which states can be reached from it
      String from = currentlyChecked.iterator().next();
      currentlyChecked.remove(from);
      ReachableStateCollector reachableStateCollector = new ReachableStateCollector(from);
      traverser = UMLStatechartsMill.traverser();
      traverser.add4SCBasis(reachableStateCollector);
      ast.accept(traverser);
      for (String to : reachableStateCollector.getReachableStates()) {
        if (!reachableStates.contains(to)) {
          // In case a new reachable state is found, add it to the open list
          // and mark it as reachable
          reachableStates.add(to);
          currentlyChecked.add(to);
          statesToBeChecked.remove(to);
        }
      }
      // Handle all inner initial states
      Optional<SCStateSymbol> stateSymbol = ast.getEnclosingScope().resolveSCState(from);
      stateCollector.getStatesMap().clear();
      traverser.add4SCBasis(stateCollector);
      traverser.add4SCStateHierarchy(stateCollector);
      if (!stateSymbol.isPresent())
        throw new IllegalStateException("Failed to resolve state symbol " + from);
      stateSymbol.get().getAstNode().accept(traverser);
      for (ASTSCState innerReachableState : stateCollector.getStates(1)) {
        if (innerReachableState.getSCModifier().isInitial()) {
          reachableStates.add(innerReachableState.getName());
          currentlyChecked.add(innerReachableState.getName());
          statesToBeChecked.remove(innerReachableState.getName());
        }
      }

    }
    return "reachable: " + String.join(",", reachableStates) + System.lineSeparator()
      + "unreachable: " + String.join(",", statesToBeChecked) + System.lineSeparator() ;
  }

  public String reportBranchingDegree(ASTSCArtifact ast) {
    BranchingDegreeCalculator branchingDegreeCalculator = new BranchingDegreeCalculator();
    UMLStatechartsTraverser traverser = UMLStatechartsMill.traverser();
    traverser.add4SCBasis(branchingDegreeCalculator);
    ast.accept(traverser);
    return branchingDegreeCalculator.getBranchingDegrees().entrySet().stream()
      .map(e -> e.getKey() + ": " + e.getValue())
      .collect(Collectors.joining(System.lineSeparator())) + System.lineSeparator();
  }

  public String reportStateNames(ASTSCArtifact ast) {
    StateCollector stateCollectorVisitor = new StateCollector();
    UMLStatechartsTraverser traverser = UMLStatechartsMill.traverser();
    traverser.add4SCBasis(stateCollectorVisitor);
    ast.accept(traverser);
    return String.join(", ", stateCollectorVisitor.getStates()
      .stream().map(e -> e.getName()).collect( Collectors.toSet())) + System.lineSeparator();
  }

  /**
   * Checks whether ast satisfies all CoCos.
   *
   * @param ast The ast of the SC.
   */
  @Override
  public void runDefaultCoCos(ASTSCArtifact ast) {
    UMLStatechartsCoCoChecker checker = new UMLStatechartsCoCoChecker();
    checker.addCoCo(new UniqueStates());
    checker.addCoCo(new TransitionSourceTargetExists());
    UMLStatechartsTraverser t = UMLStatechartsMill.traverser();
    t.setSCStateHierarchyHandler(new NoSubstatesHandler());
    checker.addCoCo(new AtLeastOneInitialState(t));
    checker.addCoCo(new CapitalStateNames());
    checker.addCoCo(new PackageCorrespondsToFolders());
    checker.addCoCo(new SCFileExtension());
    checker.addCoCo(new SCNameIsArtifactName());
    checker.addCoCo(new NonCapitalEventNames());
    checker.addCoCo(new NonCapitalParamNames());
    checker.checkAll(ast);
  }

  /**
   * Prints the contents of the SC-AST to stdout or a specified file.
   *
   * @param scartifact The SC-AST to be pretty printed
   * @param file The target file name for printing the SC artifact. If empty,
   *          the content is printed to stdout instead
   */
  @Override
  public void prettyPrint(ASTSCArtifact scartifact, String file) {
    // pretty print AST
    UMLStatechartsFullPrettyPrinter prettyPrinterDelegator
      = new UMLStatechartsFullPrettyPrinter();
    String prettyOutput = prettyPrinterDelegator.prettyprint(scartifact);
    print(prettyOutput, file);
  }

  public void print(String content, String path, String file) {
    print(content, path.isEmpty()?path : path + "/"+ file);
  }

  /**
   * Prints the contents of the SD-AST to stdout or
   * generates the java classes for the generated SD-AST into a directory
   *
   * @param scartifact The SC-AST to be converted
   * @param outputDirectory The target directory name for outputting the CD artifact. If empty,
   *          the content is printed to stdout instead
   */
  public void generateCD(ASTSCArtifact scartifact, String outputDirectory, String configTemplate, String templatePath, String handcodedPath) {
    // pretty print AST
    SC2CDConverter converter = new SC2CDConverter();

    GeneratorSetup setup = new GeneratorSetup();
    GlobalExtensionManagement glex = new GlobalExtensionManagement();
    setup.setGlex(glex);


    if (!outputDirectory.isEmpty()){
      // Prepare CD4C
      File targetDir = new File(outputDirectory);
      if (!targetDir.exists())
        targetDir.mkdirs();
      setup.setOutputDirectory(targetDir);
      setup.setTracing(false);
    }

    SC2CDData sc2CDData = converter.doConvert(scartifact, setup);

    if (!handcodedPath.isEmpty()) {
      SCTopDecorator topDecorator = new SCTopDecorator(new MCPath(handcodedPath));
      topDecorator.decorate(sc2CDData.getCompilationUnit());
    }

    if (outputDirectory.isEmpty()) {
      CD4CodeFullPrettyPrinter prettyPrinter = new CD4CodeFullPrettyPrinter();
      String prettyOutput = prettyPrinter.prettyprint(sc2CDData.getCompilationUnit());
      print(prettyOutput, outputDirectory);
    }else{
      final CD4CodeFullPrettyPrinter printer = new CD4CodeFullPrettyPrinter(new IndentPrinter());
      GeneratorEngine generatorEngine = new GeneratorEngine(setup);

      Path packageDir = Paths.get(".");
      for (String pn : sc2CDData.getCompilationUnit().getCDPackageList()){
        packageDir = Paths.get(packageDir.toString(), pn);
      }

      if (!configTemplate.isEmpty()) {
        // Load a custom config template, if set
        GeneratorSetup templateSetup = new GeneratorSetup();
        templateSetup.setGlex(glex);

        if (!templatePath.isEmpty()) {
          List<File>  files = Lists.newArrayList();
          try (Stream<Path> paths = Files.walk(Paths.get(templatePath))) {
            paths
                .filter(Files::isRegularFile)
                .forEach(f -> files.add(f.toFile()));
          }
          catch (IOException e) {
            Log.error("0x5C700 Incorrect template path "+ templatePath);
          }
        }

        TemplateController tc = templateSetup.getNewTemplateController(configTemplate);
        TemplateHookPoint hp = new TemplateHookPoint(configTemplate);
        // Provide the glex, a template helper, and the CD classes as args
        List<Object> args = Arrays.asList(templateSetup.getGlex(),
                                          new HookPointService(),
                                          sc2CDData.getScClass(),
                                          sc2CDData.getStateSuperClass(),
                                          sc2CDData.getStateClasses());
        hp.processValue(tc, sc2CDData.getCompilationUnit(), args);
      }

      for (ASTCDClass clazz : sc2CDData.getCompilationUnit().getCDDefinition().getCDClassesList()) {
        Path out = Paths.get(packageDir.toString(), clazz.getName() + ".java");
        generatorEngine.generate("de.monticore.sc2cd.gen.Class", out,
                                 clazz, printer, sc2CDData.getCompilationUnit().getCDPackageList());
      }

    }
  }

  /**
   * Initializes the standard CLI options for the Statechart tool.
   *
   * @return The CLI options with arguments.
   */
  @Override
  public Options addStandardOptions(Options options) {
    // help dialog
    options.addOption(Option.builder("h")
      .longOpt("help")
      .desc("Prints this help dialog")
      .build());

    // parse input file
    options.addOption(Option.builder("i")
      .longOpt("input")
      .argName("file")
      .hasArg()
      .desc("Reads the source file (mandatory) and parses the contents as a statechart")
      .build());

    // pretty print SC
    options.addOption(Option.builder("pp")
      .longOpt("prettyprint")
      .argName("file")
      .optionalArg(true)
      .numberOfArgs(1)
      .desc("Prints the Statechart-AST to stdout or the specified file (optional)")
      .build());

    // pretty print SC
    options.addOption(Option.builder("s")
      .longOpt("symboltable")
      .argName("file")
      .hasArg()
      .desc("Serialized the Symbol table of the given Statechart")
      .build());

    // reports about the SC
    options.addOption(Option.builder("r")
      .longOpt("report")
      .argName("dir")
      .hasArg(true)
      .desc("Prints reports of the statechart artifact to the specified directory. Available reports:"
        + System.lineSeparator() + "reachable states, branching degree, and state names")
      .build());

    // model paths
    options.addOption(Option.builder("path")
      .hasArgs()
      .desc("Sets the artifact path for imported symbols, space separated.")
      .build());

    return options;
  }

  /**
   * Initializes the additional CLI options for the Statechart tool.
   *
   * @return The CLI options with arguments.
   */
  @Override
  public Options addAdditionalOptions(Options options) {
    // convert to state pattern CD
    options.addOption(Option.builder("gen")
        .longOpt("generate")
        .argName("dir")
        .optionalArg(true)
        .numberOfArgs(1)
        .desc("Prints the state pattern CD-AST to stdout or the generated java classes to the specified folder (optional)")
        .build());

    // configTemplate parameter
    options.addOption(Option.builder("ct")
        .longOpt("configTemplate")
        .argName("file")
        .optionalArg(true)
        .numberOfArgs(1)
        .desc("Provides a config template (optional)")
        .build());

    // handcoded gen path parameter
    options.addOption(Option.builder("hcp")
        .longOpt("handcodedPath")
        .argName("pathlist")
        .optionalArg(true)
        .numberOfArgs(1)
        .desc("List of directories to look for handwritten code to integrate (optional)")
        .build());


    // templatePath parameter
    options.addOption(Option.builder("fp")
        .longOpt("templatePath")
        .argName("pathlist")
        .optionalArg(true)
        .numberOfArgs(1)
        .desc("List of directories to look for handwritten templates to integrate (optional)")
        .build());

    // model paths
    options.addOption(Option.builder("path")
        .argName("pathlist")
        .hasArgs()
        .desc("Sets the artifact path for imported symbols, space separated.")
        .build());
  
    // transformation as input
    options.addOption(Option.builder("tg")
        .longOpt("transformationGen")
        .argName("transformation")
        .hasArg()
        .desc("Generates Java code for a given transformation ")
        .build());
  
    // specify custom output directory
    options.addOption(Option.builder("o")
        .longOpt("out")
        .argName("path")
        .hasArg()
        .desc("Output directory for all generated artifacts.")
        .build());

    return options;
  }
}
