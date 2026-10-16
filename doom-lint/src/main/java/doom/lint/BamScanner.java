package doom.lint;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Finds signed operations on {@code @Bam} values in one class.
 *
 * <h2>What is a BAM operand</h2>
 * An expression is a BAM value when its <b>declared</b> type carries {@code @doom.math.annotation.Bam}:
 * <ul>
 *   <li>a local variable, parameter, field or record component declared {@code @Bam int};</li>
 *   <li>a call of a method whose return type is {@code @Bam int};</li>
 *   <li>an element of an array declared {@code @Bam int[]} (the annotation applies to the elements);</li>
 *   <li>a cast to {@code (@Bam int)}.</li>
 * </ul>
 * The property also flows through the operations that keep a value in the angle domain: parentheses, unary
 * {@code -}, {@code +} and {@code ~}, binary {@code +} and {@code -} (when either side is a BAM value), increments,
 * assignments and compound assignments (the value of the variable), and {@code ?:} (when either branch is a BAM value).
 * A cast to a type without {@code @Bam}, such as {@code (int) angle}, ends it: that is the explicit way to treat an
 * angle as a signed number on purpose.
 *
 * <h2>What is reported</h2>
 * <ul>
 *   <li>{@code <}, {@code >}, {@code <=}, {@code >=} with a BAM value on either side (use {@code Angle.lt/le/gt/ge});</li>
 *   <li>{@code >>} and {@code >>=} with a BAM value on the left (use {@code >>>});</li>
 *   <li>{@code /}, {@code %}, {@code /=} and {@code %=} with a BAM value on either side
 *       (use {@code Integer.divideUnsigned} / {@code Integer.remainderUnsigned}).</li>
 * </ul>
 * A report can be suppressed with {@code @SuppressWarnings("BamLint")} on an enclosing declaration
 * (variable, method or class).
 */
final class BamScanner extends TreePathScanner<Void, Void> {

    /** Qualified name of the annotation. Matched by name, so this module does not depend on {@code doom.math}. */
    static final String BAM = "doom.math.annotation.Bam";
    /** The {@code @SuppressWarnings} key. */
    static final String SUPPRESS_KEY = "BamLint";

    private final Trees trees;
    private final CompilationUnitTree unit;
    private final Diagnostic.Kind kind;
    private final Set<Tree> scannedClasses;
    private boolean first = true;

    BamScanner(Trees trees, CompilationUnitTree unit, Diagnostic.Kind kind, Set<Tree> scannedClasses) {
        this.trees = trees;
        this.unit = unit;
        this.kind = kind;
        this.scannedClasses = scannedClasses;
    }

    // ------------------------------------------------------------------------------------------- visitors

    @Override
    public Void visitClass(ClassTree tree, Void unused) {
        if (first) {
            first = false;
        } else if (!scannedClasses.add(tree)) {
            return null; // nested class already scanned through its own event
        }
        return super.visitClass(tree, unused);
    }

    @Override
    public Void visitBinary(BinaryTree tree, Void unused) {
        TreePath path = getCurrentPath();
        switch (tree.getKind()) {
            case LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL -> {
                if (isBam(tree.getLeftOperand(), path) || isBam(tree.getRightOperand(), path)) {
                    report(tree, "signed comparison '" + operator(tree.getKind())
                            + "' of a @Bam angle; use Angle.lt/le/gt/ge (unsigned)");
                }
            }
            case RIGHT_SHIFT -> {
                if (isBam(tree.getLeftOperand(), path)) {
                    report(tree, "arithmetic shift '>>' of a @Bam angle; use '>>>'");
                }
            }
            case DIVIDE, REMAINDER -> {
                if (isBam(tree.getLeftOperand(), path) || isBam(tree.getRightOperand(), path)) {
                    report(tree, "signed division '" + operator(tree.getKind())
                            + "' of a @Bam angle; use Integer.divideUnsigned / Integer.remainderUnsigned");
                }
            }
            default -> {
                // nothing to check
            }
        }
        return super.visitBinary(tree, unused);
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void unused) {
        TreePath path = getCurrentPath();
        switch (tree.getKind()) {
            case RIGHT_SHIFT_ASSIGNMENT -> {
                if (isBam(tree.getVariable(), path)) {
                    report(tree, "arithmetic shift '>>=' of a @Bam angle; use '>>>='");
                }
            }
            case DIVIDE_ASSIGNMENT, REMAINDER_ASSIGNMENT -> {
                if (isBam(tree.getVariable(), path) || isBam(tree.getExpression(), path)) {
                    report(tree, "signed division '" + operator(tree.getKind())
                            + "' of a @Bam angle; use Integer.divideUnsigned / Integer.remainderUnsigned");
                }
            }
            default -> {
                // nothing to check
            }
        }
        return super.visitCompoundAssignment(tree, unused);
    }

    // ------------------------------------------------------------------------------------------- BAM detection

    /** Whether the value of {@code expr} (a child of {@code parent}'s leaf) is a BAM angle. */
    boolean isBam(ExpressionTree expr, TreePath parent) {
        TreePath path = new TreePath(parent, expr);
        return switch (expr.getKind()) {
            case PARENTHESIZED -> isBam(((ParenthesizedTree) expr).getExpression(), path);
            case IDENTIFIER, MEMBER_SELECT, METHOD_INVOCATION, ARRAY_ACCESS -> {
                TypeMirror type = declaredType(expr, parent);
                yield type != null && type.getKind() != TypeKind.ARRAY && hasBam(type);
            }
            case TYPE_CAST -> typeTreeHasBam(((TypeCastTree) expr).getType(), path);
            case ASSIGNMENT -> isBam(((AssignmentTree) expr).getVariable(), path);
            case PLUS_ASSIGNMENT, MINUS_ASSIGNMENT, MULTIPLY_ASSIGNMENT, DIVIDE_ASSIGNMENT, REMAINDER_ASSIGNMENT,
                    AND_ASSIGNMENT, OR_ASSIGNMENT, XOR_ASSIGNMENT, LEFT_SHIFT_ASSIGNMENT, RIGHT_SHIFT_ASSIGNMENT,
                    UNSIGNED_RIGHT_SHIFT_ASSIGNMENT -> isBam(((CompoundAssignmentTree) expr).getVariable(), path);
            case CONDITIONAL_EXPRESSION -> {
                ConditionalExpressionTree c = (ConditionalExpressionTree) expr;
                yield isBam(c.getTrueExpression(), path) || isBam(c.getFalseExpression(), path);
            }
            case PLUS, MINUS -> {
                BinaryTree b = (BinaryTree) expr;
                yield isBam(b.getLeftOperand(), path) || isBam(b.getRightOperand(), path);
            }
            case UNARY_MINUS, UNARY_PLUS, BITWISE_COMPLEMENT, PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT,
                    POSTFIX_DECREMENT -> isBam(((UnaryTree) expr).getExpression(), path);
            default -> false;
        };
    }

    /**
     * The declared type (with its type annotations) of a variable reference, method call or array element, or null
     * for any other expression.
     */
    private TypeMirror declaredType(ExpressionTree expr, TreePath parent) {
        TreePath path = new TreePath(parent, expr);
        switch (expr.getKind()) {
            case PARENTHESIZED -> {
                return declaredType(((ParenthesizedTree) expr).getExpression(), path);
            }
            case IDENTIFIER, MEMBER_SELECT -> {
                Element e = trees.getElement(path);
                return e instanceof VariableElement v ? v.asType() : null;
            }
            case METHOD_INVOCATION -> {
                ExpressionTree select = ((MethodInvocationTree) expr).getMethodSelect();
                Element e = trees.getElement(new TreePath(path, select));
                return e instanceof ExecutableElement m ? m.getReturnType() : null;
            }
            case ARRAY_ACCESS -> {
                TypeMirror array = declaredType(((ArrayAccessTree) expr).getExpression(), path);
                return array instanceof ArrayType a ? a.getComponentType() : null;
            }
            default -> {
                return null;
            }
        }
    }

    private static boolean hasBam(TypeMirror type) {
        return containsBam(type.getAnnotationMirrors());
    }

    private static boolean containsBam(List<? extends AnnotationMirror> annotations) {
        for (AnnotationMirror a : annotations) {
            Element e = a.getAnnotationType().asElement();
            if (e instanceof TypeElement t && t.getQualifiedName().contentEquals(BAM)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a type written in the source (a cast target) is annotated {@code @Bam}. */
    private boolean typeTreeHasBam(Tree type, TreePath parent) {
        if (type instanceof AnnotatedTypeTree annotated) {
            TreePath path = new TreePath(parent, annotated);
            for (AnnotationTree a : annotated.getAnnotations()) {
                Element e = trees.getElement(new TreePath(new TreePath(path, a), a.getAnnotationType()));
                if (e instanceof TypeElement t && t.getQualifiedName().contentEquals(BAM)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------- reporting

    private void report(Tree tree, String message) {
        if (!suppressed()) {
            trees.printMessage(kind, "[" + BamLintPlugin.NAME + "] " + message, tree, unit);
        }
    }

    /** Whether an enclosing declaration carries {@code @SuppressWarnings("BamLint")}. */
    private boolean suppressed() {
        for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
            Tree leaf = p.getLeaf();
            if (leaf instanceof ClassTree || leaf instanceof MethodTree || leaf instanceof VariableTree) {
                Element e = trees.getElement(p);
                SuppressWarnings sw = e == null ? null : e.getAnnotation(SuppressWarnings.class);
                if (sw != null) {
                    for (String key : sw.value()) {
                        if (key.equals(SUPPRESS_KEY)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static String operator(Tree.Kind kind) {
        return switch (kind) {
            case LESS_THAN -> "<";
            case GREATER_THAN -> ">";
            case LESS_THAN_EQUAL -> "<=";
            case GREATER_THAN_EQUAL -> ">=";
            case DIVIDE -> "/";
            case REMAINDER -> "%";
            case DIVIDE_ASSIGNMENT -> "/=";
            case REMAINDER_ASSIGNMENT -> "%=";
            default -> kind.toString();
        };
    }
}
