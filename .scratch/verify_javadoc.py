"""校验注释覆盖及非注释 token 一致性，并准备标准 Javadoc 校验参数。"""
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent

def tokens(source):
    source = source.replace('\r\n', '\n')
    result = []
    pos = 0
    while pos < len(source):
        if source[pos].isspace():
            pos += 1
            continue
        if source.startswith('//', pos):
            end = source.find('\n', pos)
            pos = len(source) if end < 0 else end
            continue
        if source.startswith('/*', pos):
            end = source.find('*/', pos + 2)
            assert end >= 0, '未闭合注释'
            pos = end + 2
            continue
        if source[pos] in ('"', "'"):
            delimiter = '"""' if source.startswith('"""', pos) else source[pos]
            end = pos + len(delimiter)
            while end < len(source):
                if source[end] == '\\':
                    end += 2
                elif source.startswith(delimiter, end):
                    end += len(delimiter)
                    break
                else:
                    end += 1
            result.append(source[pos:end])
            pos = end
            continue
        match = re.match(r'[\w$]+|>>>=|>>>|>>=|<<=|>>|<<|==|!=|<=|>=|&&|\|\||\+\+|--|\+=|-=|\*=|/=|%=|&=|\|=|\^=|->|::|\.\.\.|[^\s]', source[pos:])
        assert match, (pos, source[pos:pos+20])
        result.append(match[0])
        pos += len(match[0])
    return result

before = json.loads((ROOT / 'javadoc-before.json').read_text(encoding='utf-8'))
for filename, original in before.items():
    current = Path(filename).read_bytes().decode('utf-8')
    assert tokens(original) == tokens(current), '非注释代码变化：' + filename
print(f'通过：{len(before)} 个文件的非注释 Java token 与修改前完全一致。')

parsed = subprocess.run(['java', str(ROOT/'JavadocInventory.java'), str(ROOT/'javadoc-files.txt')], capture_output=True)
assert parsed.returncode == 0 and not parsed.stderr, parsed.stderr.decode('utf-8', errors='replace')
(ROOT/'javadoc-after.jsonl').write_bytes(parsed.stdout)
declarations = [json.loads(line) for line in parsed.stdout.decode('utf-8').splitlines()]
for declaration in declarations:
    doc = declaration['doc']
    assert doc, declaration
    for name in declaration['params']:
        assert re.search(r'@param\s+' + re.escape(name) + r'\s+\S', doc), declaration
    if declaration['kind'] == 'METHOD' and declaration['type'] != 'void':
        assert re.search(r'@return\s+\S', doc), declaration
print(f'通过：{len(declarations)} 个类型、字段、构造器及方法声明均有关联 Javadoc，参数与返回值标签齐全。')

classpath = list((REPO/'backend').glob('*/target/classes'))
classpath += list((REPO/'backend').glob('*/target/test-classes'))
report = REPO/'backend/start/target/surefire-reports/TEST-com.dianping.shopdetail.ShopDetailIntegrationTest.xml'
test_properties = ET.parse(report).findall('./properties/property')
actual_classpath = next(p.attrib['value'] for p in test_properties if p.attrib['name']=='java.class.path')
classpath += [Path(p) for p in actual_classpath.split(';') if p]
classpath.append(Path.home()/'.m2/repository/org/projectlombok/lombok/1.18.42/lombok-1.18.42.jar')
arguments = ['-quiet','--no-platform-links','-d',str(ROOT/'javadoc-html'),'-encoding','UTF-8','-docencoding','UTF-8',
             '-charset','UTF-8','-private','-Xdoclint:all','-classpath',';'.join(p.as_posix() for p in classpath)]
arguments += [Path(filename).as_posix() for filename in before]
argument_file = ROOT/'javadoc-options.txt'
argument_file.write_text('\n'.join('"'+argument.replace('\\','/')+'"' for argument in arguments), encoding='utf-8')
print('已准备标准 Javadoc 格式、引用及标签校验。')
