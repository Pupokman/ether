package net.irisshaders.iris.pipeline.transform.transformer;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.LiteralExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.LayoutQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.NamedLayoutQualifierPart;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifierPart;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.query.match.Matcher;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import io.github.douira.glsl_transformer.ast.transform.Template;
import io.github.douira.glsl_transformer.parser.ParseShape;
import io.github.douira.glsl_transformer.util.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Adds explicit Vulkan/ShaderC vertex input locations to Iris-transformed GLSL.
 * OpenGL Iris used glBindAttribLocation() for this at link time.
 */
public final class VulkanVertexInputLayoutTransformer {
    private static final Set<String> VANILLA_ATTRIBUTE_NAMES = Set.of(
        "Position", "Color", "Normal", "UV0", "UV1", "UV2", "UV3", "LineWidth");
    private static final Pattern VERSION = Pattern.compile("#version\\s+(\\d+)");

    private static final Matcher<ExternalDeclaration> NON_LAYOUT_IN = new Matcher<>(
        "in float name;", ParseShape.EXTERNAL_DECLARATION) {
        {
            markClassWildcard("qualifier", pattern.getRoot().nodeIndex.getUnique(TypeQualifier.class));
            markClassWildcard("type", pattern.getRoot().nodeIndex.getUnique(BuiltinNumericTypeSpecifier.class));
            markClassWildcard(
                "name*",
                pattern.getRoot().identifierIndex.getUnique("name").getAncestor(DeclarationMember.class));
        }

        @Override
        public boolean matchesExtract(ExternalDeclaration tree) {
            if (!super.matchesExtract(tree)) {
                return false;
            }

            TypeQualifier qualifier = getNodeMatch("qualifier", TypeQualifier.class);
            boolean hasIn = false;
            for (TypeQualifierPart part : qualifier.getParts()) {
                if (part instanceof StorageQualifier storage && storage.storageType == StorageType.IN) {
                    hasIn = true;
                } else if (part instanceof LayoutQualifier) {
                    return false;
                }
            }

            return hasIn;
        }
    };

    private static final Template<ExternalDeclaration> INPUT_TEMPLATE =
        Template.withExternalDeclaration("in __type __name;");

    static {
        INPUT_TEMPLATE.markLocalReplacement(
            INPUT_TEMPLATE.getSourceRoot().nodeIndex.getOne(TypeQualifier.class));
        INPUT_TEMPLATE.markLocalReplacement(
            "__type",
            io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier.class);
        INPUT_TEMPLATE.markLocalReplacement("__name", DeclarationMember.class);
    }

    private VulkanVertexInputLayoutTransformer() {
    }

    public static String transform(String source, VertexFormat format, boolean fallback) {
        if (source == null) {
            return null;
        }

        java.util.regex.Matcher version = VERSION.matcher(source);
        if (!version.find()) {
            throw new IllegalArgumentException("No #version directive found");
        }

        Map<String, Integer> locations = attributeLocations(format, fallback);
        SingleASTTransformer<JobParameters> transformer = new SingleASTTransformer<>();
        transformer.setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
        transformer.getLexer().version =
            io.github.douira.glsl_transformer.ast.node.Version.fromNumber(
                Integer.parseInt(version.group(1)));
        transformer.setTransformation(
            (tree, root) -> root.indexBuildSession(() -> patch(tree, root, locations)));

        return transformer.transform(source);
    }

    static Map<String, Integer> attributeLocations(VertexFormat format, boolean fallback) {
        Map<String, Integer> result = new HashMap<>();
        List<VertexFormatElement> elements = format.getElements();

        for (int location = 0; location < elements.size(); location++) {
            String name = elements.get(location).name();
            String shaderName =
                !fallback && VANILLA_ATTRIBUTE_NAMES.contains(name) ? "iris_" + name : name;
            result.put(shaderName, location);
        }

        return result;
    }

    private static void patch(
            TranslationUnit tree,
            Root root,
            Map<String, Integer> locations) {
        List<ExternalDeclaration> remove = new ArrayList<>();
        List<ExternalDeclaration> add = new ArrayList<>();

        for (DeclarationExternalDeclaration declaration :
                new ArrayList<>(root.nodeIndex.get(DeclarationExternalDeclaration.class))) {
            if (!NON_LAYOUT_IN.matchesExtract(declaration)) {
                continue;
            }

            List<DeclarationMember> members =
                NON_LAYOUT_IN.getNodeMatch("name*", DeclarationMember.class)
                    .getAncestor(TypeAndInitDeclaration.class)
                    .getMembers();
            TypeQualifier qualifier =
                NON_LAYOUT_IN.getNodeMatch("qualifier", TypeQualifier.class);
            BuiltinNumericTypeSpecifier type =
                NON_LAYOUT_IN.getNodeMatch("type", BuiltinNumericTypeSpecifier.class);

            int moved = 0;
            int originalMemberCount = members.size();

            for (DeclarationMember member : List.copyOf(members)) {
                Integer location = locations.get(member.getName().getName());
                if (location == null) {
                    continue;
                }

                member.detach();
                TypeQualifier q = qualifier.cloneInto(root);
                q.getChildren().add(
                    0,
                    new LayoutQualifier(
                        Stream.of(
                            new NamedLayoutQualifierPart(
                                new Identifier("location"),
                                new LiteralExpression(Type.INT32, location)))));

                add.add(INPUT_TEMPLATE.getInstanceFor(root, q, type.cloneInto(root), member));
                moved++;
            }

            if (moved == originalMemberCount) {
                remove.add(declaration);
            }
        }

        tree.getChildren().removeAll(remove);
        for (ExternalDeclaration declaration : remove) {
            declaration.detachParent();
        }
        tree.injectNodes(ASTInjectionPoint.BEFORE_DECLARATIONS, add);
    }
}
