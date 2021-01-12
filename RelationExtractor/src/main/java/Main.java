import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import org.apache.commons.io.FilenameUtils;

/**
 * JavaMethodParserのエントリーポイント用クラス
 */
public class Main {
    final static int[] FILE_NUM = { 0 };
    final static long[] totalResolveSuccess = { 0 };
    final static long[] totalResolveFailed = { 0 };
    final static int[] resolveSuccess = { 0 };
    final static int[] resolveFailed = { 0 };

    final static boolean dividesByProject = true;

    public static void main(String[] args) throws IOException, InterruptedException {
        File dataRoot = Paths.get("data").toAbsolutePath().normalize().toFile();
        File rawDataDir = Paths.get(dataRoot.getAbsolutePath(), "processed_data").toFile();
        File undividedInputDir = Paths.get(dataRoot.getAbsolutePath(), "undivided_input").toFile();
        File inputDir = Paths.get(dataRoot.getAbsolutePath(), "input").toFile();

        Random rand = new Random(0L);
        rand.nextInt(5);

        long start = System.nanoTime();
        int projectCount = 0;
        for (File subsetDir : rawDataDir.listFiles()) {
            File undividedInputSubsetDir = Paths.get(undividedInputDir.getAbsolutePath(), subsetDir.getName()).toFile();
            File[] projects = subsetDir.listFiles();
            LinkedList<String> projectNames1 = new LinkedList<>(Arrays.asList(projects).stream()
                    .map(projectRootDir -> formatDirName(getPrefix(projectRootDir.getName())))
                    .collect(Collectors.toList()));
            LinkedList<String> projectNames2 = new LinkedList<>();
            for (File projectRootDir : projects) {
                if (!projectRootDir.isDirectory())
                    continue;

                int testProjectIndex = projectCount % 5 + 1;
                int valProjectIndex = projectCount % 5 + 2;
                projectCount += 1;

                resolveSuccess[0] = 0;
                resolveFailed[0] = 0;
                String formattedProjectName = formatDirName(getPrefix(projectRootDir.getName()));
                System.out.println("\n\n-----\n");
                System.out.println(formattedProjectName);

                projectNames1.removeFirstOccurrence(formattedProjectName);
                projectNames2.addLast(formattedProjectName);

                if (formattedProjectName.equals("apachehive")) {
                    continue;
                }

                System.out.println(projectNames1);
                System.out.println(projectNames2);
                System.out.println("testProjectIndex : " + testProjectIndex);

                String srcDirAbsPath = projectRootDir.getAbsolutePath();

                /*
                 * 取得元ディレクトリからJavaファイルを再帰的に探索
                 */
                ArrayList<File> fileArrayList = getJavaFilesRecursively(projectRootDir);
                System.out.println(fileArrayList.size() + " Java files found.");

                /*
                 * Solverの準備
                 */
                TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
                TypeSolver javaParserTypeSolver = new JavaParserTypeSolver(new File(srcDirAbsPath));
                // TypeSolver platform_framework_base = new JavaParserTypeSolver(
                // new File(rawDataDir.getAbsolutePath() + "/platformframeworksbase"));
                reflectionTypeSolver.setParent(reflectionTypeSolver);
                CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
                combinedSolver.add(reflectionTypeSolver);
                combinedSolver.add(javaParserTypeSolver);
                // combinedSolver.add(platform_framework_base);
                JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);
                JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver);

                // String out = cmd.getOptionValue("outputdir");

                for (File javaFile : fileArrayList) {
                    FILE_NUM[0] += 1;
                    String javaFileRelPath = javaFile.getAbsolutePath().replace(subsetDir.getAbsolutePath(), "");
                    String parentRelPath = javaFileRelPath.replace(javaFile.getName(), "");

                    List<File> jsonFiles = true ? makeUndividedInput(javaFile, undividedInputSubsetDir, parentRelPath)
                            : findExistingJsons(undividedInputSubsetDir.getAbsolutePath(), parentRelPath,
                                    getPrefix(javaFile.getName()));

                    int randomNum = rand.nextInt(5) + 1;
                    for (int i = 1; i <= 5; i++) {
                        String trainOrValOrTest = (dividesByProject && i == testProjectIndex) // データセットをプロジェクトごとに分割する場合
                                || (!dividesByProject && i == randomNum) // ファイル単位でデータセットを分割する場合
                                        ? "test"
                                        : (dividesByProject && i == valProjectIndex) // データセットをプロジェクトごとに分割する場合
                                                // || (!dividesByProject && i == randomNum) // ファイル単位でデータセットを分割する場合
                                                ? "val"
                                                : "train";
                        // String destDirPath = out + i + "/" + trainORtest;
                        Path destDir = Paths.get(inputDir.getAbsolutePath(), Integer.toString(i), trainOrValOrTest);

                        for (File jsonFile : jsonFiles) {
                            Path jsonRelPath = undividedInputSubsetDir.toPath().relativize(jsonFile.toPath());
                            Path symlink = Paths.get(destDir.toString(), jsonRelPath.toString());
                            Files.createDirectories(symlink.getParent());
                            ProcessBuilder pb = new ProcessBuilder("ln", "-sf",
                                    symlink.getParent().relativize(jsonFile.toPath()).toString(), symlink.toString());
                            pb.start();
                        }
                    }
                }
                totalResolveSuccess[0] += resolveSuccess[0];
                totalResolveFailed[0] += resolveFailed[0];
                System.out.println();
                System.out.println("resolveSuccess : " + resolveSuccess[0]);
                System.out.println("resolveFailed : " + resolveFailed[0]);
            }
        }

        long time = System.nanoTime() - start;
        int hour = (int) (time / 3600000000000L);
        int minute = (int) ((time - (long) hour * 3600000000000L) / 60000000000L);
        int second = (int) ((time - (long) hour * 3600000000000L) - (long) minute * 60000000000L / 1000000000L);
        System.out.println("parse & resolve time : " + hour + "時間" + minute + "分" + second + "秒");
        System.out.println("totalResolveSuccess : " + totalResolveSuccess[0]);
        System.out.println("totalResolveFailed : " + totalResolveFailed[0]);
        System.out.println("FILE_NUM : " + FILE_NUM[0]);
    }

    private static List<File> makeUndividedInput(File javaFile, File destDirAbsPath, String parentRelPath)
            throws IOException, FileNotFoundException {
        System.out.print("\r");
        System.out.print("Parsing : " + javaFile.getName());
        CompilationUnit cu = JavaParser.parse(javaFile);

        Resolver resolver = new Resolver(true);
        resolver.execute(cu);

        resolveSuccess[0] += resolver.getResolveSuccess();
        resolveFailed[0] += resolver.getResolveFailed();

        String fileName = getPrefix(javaFile.getName());
        List<File> jsonFiles = makeJsons(resolver, destDirAbsPath.getAbsolutePath(), parentRelPath, fileName);

        // Map<String, String> methodASTPaths = resolver.getMethodASTPaths();
        // if (!methodASTPaths.isEmpty()) {
        // File file = new File(
        // Paths.get(destDirAbsPath, "methodASTPath", fileName + ".txt").toString());
        // file.getParentFile().mkdirs();
        // try (PrintWriter out = new PrintWriter(file)) {
        // for (Entry<String, String> entry : methodASTPaths.entrySet()) {
        // String declarationMethodName = entry.getKey();
        // String astPath = entry.getValue();
        // out.print(declarationMethodName);
        // out.print(' ');
        // out.println(astPath);
        // }
        // }
        // }

        return jsonFiles;
    }

    private static List<File> findExistingJsons(String destDirAbsPath, String parentRelPath, String fileName) {
        List<File> jsonFiles = new ArrayList<>();
        for (String key : Arrays.asList("classExtends", "methodInClass", "fieldInClass", "methodCall", "fieldInMethod",
                "returnType", "fieldType")) {
            File jsonFile = Paths.get(destDirAbsPath, "relations", key, parentRelPath, fileName + ".json")
                    .toAbsolutePath().normalize().toFile();
            if (jsonFile.exists()) {
                jsonFiles.add(jsonFile);
            }
        }
        return jsonFiles;
    }

    private static List<File> makeJsons(Resolver resolver, String destDirAbsPath, String parentRelPath, String fileName)
            throws IOException {
        List<File> jsonFiles = new ArrayList<>();
        for (Entry<String, HashMap<String, ArrayList<String>>> relation : resolver.getRelations().entrySet()) {
            JsonGenerator jsonGenerator = new JsonGenerator(relation.getValue(), true);
            File jsonFile = Paths.get(destDirAbsPath, "relations", relation.getKey(), parentRelPath, fileName + ".json")
                    .toAbsolutePath().normalize().toFile();
            jsonGenerator.saveFile(jsonFile.getAbsolutePath());
            jsonFiles.add(jsonFile);
        }
        return jsonFiles;
    }

    /**
     * ファイルの絶対パスからファイル名のみの文字列を返す
     * 
     * @param fileName ファイルパスを表す文字列
     * @return ファイル名のみの文字列
     */
    private static String getPrefix(String fileName) {
        if (fileName == null)
            return null;
        int point = fileName.lastIndexOf(".");
        if (point != -1) {
            fileName = fileName.substring(0, point);
        }
        point = fileName.lastIndexOf("/");
        if (point != -1) {
            return fileName.substring(point + 1);
        }
        return fileName;
    }

    /**
     * ディレクトリの内部に含まれるJavaファイルを再帰的に探索する
     * 
     * @param dir 探索対象のディレクトリを表すFileオブジェクト
     * @return 内部に含まれるJavaファイルを表すFileオブジェクトのArrayList
     */
    private static ArrayList<File> getJavaFilesRecursively(File dir) {
        ArrayList<File> fileList = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null)
            return new ArrayList<>();

        for (File file : files) {
            if (!file.exists()) {
                continue;
            } else if (file.isDirectory()) {
                fileList.addAll(getJavaFilesRecursively(file));
            } else if (file.isFile() && FilenameUtils.getExtension(file.getAbsolutePath()).equals("java")) {
                fileList.add(file);
            }
        }
        return fileList;
    }

    private static String formatDirName(String dirName) {
        return dirName.replace("-", "").replace("_", "");
    }

}
