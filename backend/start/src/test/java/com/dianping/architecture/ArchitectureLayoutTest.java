package com.dianping.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureLayoutTest {
    private static final Set<String> MODULES = Set.of("domain", "application",
            "infrastructure", "facade", "controller", "job",
            "listener", "service", "start");

    private Path backend() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null && !(Files.isRegularFile(path.resolve("pom.xml"))
                && Files.isRegularFile(path.resolve("start/pom.xml")))) {
            path = path.getParent();
        }
        assertNotNull(path, "必须在实际后端工程中验证模块结构");
        return path;
    }

    private Element pom(String module) throws Exception {
        Path path = module.isEmpty() ? backend().resolve("pom.xml") : backend().resolve(module).resolve("pom.xml");
        assertTrue(Files.isRegularFile(path), "缺少模块 POM：" + module);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    @Test
    void reactorContainsExactlyNineModules() throws Exception {
        var nodes = pom("").getElementsByTagName("module");
        Set<String> actual = new HashSet<>();
        for (int i = 0; i < nodes.getLength(); i++) actual.add(nodes.item(i).getTextContent().trim());
        assertEquals(MODULES, actual, "后端 reactor 必须采用九模块结构");
        for (String module : MODULES) pom(module);
    }

    @Test
    void domainHasNoProductionDependenciesAndApplicationOnlyUsesItsAllowedSeams() throws Exception {
        for (String module : Set.of("domain", "application")) {
            var dependencies = pom(module).getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                var dependency = (Element) dependencies.item(i);
                if (dependency.getElementsByTagName("scope").getLength() > 0
                        && "test".equals(dependency.getElementsByTagName("scope").item(0).getTextContent())) continue;
                String artifact = dependency.getElementsByTagName("artifactId").item(0).getTextContent();
                // 领域层仅允许 Lombok 编译期例外，不能借 provided 范围引入运行框架。
                if (module.equals("domain") && artifact.equals("lombok")
                        && dependency.getElementsByTagName("groupId").item(0).getTextContent().equals("org.projectlombok")
                        && dependency.getElementsByTagName("scope").getLength() > 0
                        && dependency.getElementsByTagName("scope").item(0).getTextContent().equals("provided")) continue;
                assertEquals("application", module, "领域模块不能声明生产依赖：" + artifact);
                assertTrue(Set.of("domain", "spring-context", "spring-tx").contains(artifact),
                        "应用层不能依赖遗留层或技术适配：" + artifact);
            }
        }
    }

    @Test
    void onlyStartDeclaresExecutableBootPackaging() throws Exception {
        for (String module : MODULES) {
            var plugins = pom(module).getElementsByTagName("plugin");
            boolean boot = false;
            for (int i = 0; i < plugins.getLength(); i++) {
                var plugin = (Element) plugins.item(i);
                boot |= "spring-boot-maven-plugin".equals(plugin.getElementsByTagName("artifactId").item(0).getTextContent());
            }
            assertEquals("start".equals(module), boot, "只有 start 可以声明 Boot 可执行打包：" + module);
        }
    }
}
