import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 只解析源码，收集文档声明的位置，不进行类型分析或修改代码。 */
class JavadocInventory {
    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        var paths = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none"), null,
                    manager.getJavaFileObjectsFromStrings(paths));
            var trees = Trees.instance(task);
            var docs = DocTrees.instance(task);
            for (var unit : task.parse()) {
                String source = unit.getSourceFile().getCharContent(true).toString();
                new TreePathScanner<Void, Void>() {
                    String owner = "";
                    void emit(Tree tree, String kind, String name, String type, List<String> params, List<String> generics) {
                        long start = trees.getSourcePositions().getStartPosition(unit, tree);
                        long end = trees.getSourcePositions().getEndPosition(unit, tree);
                        if (start < 0 || end < start) return;
                        String doc = docs.getDocComment(getCurrentPath());
                        System.out.println("{\"file\":" + quote(Path.of(unit.getSourceFile().toUri()).toString())
                                + ",\"start\":" + start + ",\"end\":" + end + ",\"kind\":" + quote(kind)
                                + ",\"owner\":" + quote(owner) + ",\"name\":" + quote(name) + ",\"type\":" + quote(type)
                                + ",\"doc\":" + (doc == null ? "null" : quote(doc))
                                + ",\"params\":[" + String.join(",", params.stream().map(JavadocInventory::quote).toList())
                                + "],\"generics\":[" + String.join(",", generics.stream().map(JavadocInventory::quote).toList()) + "]}");
                    }
                    @Override public Void visitClass(ClassTree tree, Void unused) {
                        if (tree.getSimpleName().isEmpty()) return null;
                        String previous = owner;
                        owner = previous.isEmpty() ? tree.getSimpleName().toString() : previous + "." + tree.getSimpleName();
                        long classStart = trees.getSourcePositions().getStartPosition(unit, tree);
                        List<String> components = tree.getKind() == Tree.Kind.RECORD ? tree.getMembers().stream()
                                .filter(m -> m instanceof VariableTree)
                                .filter(m -> { long p = trees.getSourcePositions().getStartPosition(unit, m);
                                    return p >= classStart && !source.substring((int) classStart, (int) p).contains("{"); })
                                .map(m -> ((VariableTree)m).getName().toString()).toList() : List.of();
                        emit(tree, tree.getKind().name(), tree.getSimpleName().toString(), "", components,
                                tree.getTypeParameters().stream().map(p -> p.getName().toString()).toList());
                        super.visitClass(tree, unused);
                        owner = previous;
                        return null;
                    }
                    @Override public Void visitMethod(MethodTree tree, Void unused) {
                        emit(tree, tree.getReturnType() == null ? "CONSTRUCTOR" : "METHOD", tree.getName().toString(),
                                tree.getReturnType() == null ? "" : tree.getReturnType().toString(),
                                tree.getParameters().stream().map(p -> p.getName().toString()).toList(),
                                tree.getTypeParameters().stream().map(p -> p.getName().toString()).toList());
                        return null;
                    }
                    @Override public Void visitVariable(VariableTree tree, Void unused) {
                        Tree parent = getCurrentPath().getParentPath().getLeaf();
                        if (parent instanceof ClassTree cls) {
                            long position = trees.getSourcePositions().getStartPosition(unit, tree);
                            long classStart = trees.getSourcePositions().getStartPosition(unit, cls);
                            String prefix = source.substring((int) classStart, (int) position);
                            if (cls.getKind() == Tree.Kind.RECORD && !prefix.contains("{")) return null;
                            emit(tree, "FIELD", tree.getName().toString(), tree.getType() == null ? "" : tree.getType().toString(), List.of(), List.of());
                        }
                        return null;
                    }
                }.scan(unit, null);
            }
        }
    }
}
