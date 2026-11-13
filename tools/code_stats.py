# -*- coding: utf-8 -*-
import os

def count_lines(file_path):
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return len(f.readlines())
    except Exception:
        return 0

def generate_report():
    # 获取项目根目录
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    src_dir = os.path.join(root_dir, 'src', 'main', 'java')
    
    if not os.path.exists(src_dir):
        print("[错误] 未找到 src/main/java 目录，请在项目根目录运行。")
        return

    total_files = 0
    total_lines = 0
    report = "# 📊 RBMK-Monitor 源码统计报告\n\n"
    report += "| 模块/包 | Java 文件数 | 代码行数 |\n"
    report += "| :--- | :--- | :--- |\n"

    # 遍历 src 目录
    for dirpath, _, filenames in os.walk(src_dir):
        java_files = [f for f in filenames if f.endswith('.java')]
        if java_files:
            pkg_lines = sum(count_lines(os.path.join(dirpath, f)) for f in java_files)
            total_files += len(java_files)
            total_lines += pkg_lines
            
            # 截取包名显示
            pkg_name = dirpath.replace(src_dir, "").replace("\\", ".").strip(".")
            if not pkg_name: pkg_name = "com.jiezhirbmkmod (根目录)"
            
            report += f"| `{pkg_name}` | {len(java_files)} | {pkg_lines} |\n"

    report += f"\n**总计**：**{total_files}** 个 Java 文件，共计 **{total_lines}** 行代码。\n"
    report += "\n*本报告由 `tools/code_stats.py` 自动生成。*\n"

    # 输出到项目根目录
    output_path = os.path.join(root_dir, 'CODE_STATS.md')
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(report)
        
    print(f"[成功] 统计完成！共 {total_files} 个文件，{total_lines} 行代码。")
    print(f"[成功] 报告已生成至: {output_path}")

if __name__ == '__main__':
    generate_report()