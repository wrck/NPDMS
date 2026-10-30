# 培训PDF中文字体

`NpdmsPrintSansSC-Regular.ttf`由[Noto CJK Sans简体中文可变字体](https://github.com/notofonts/noto-cjk/tree/main/Sans/Variable/TTF/Subset)的`NotoSansSC-VF.ttf`派生。

- 使用fontTools varLib.instancer固定`wght=400`，生成常规字重静态字体；不依赖宿主机已安装字体。
- 派生字体家族名称为`NPDMS Print Sans SC`，PostScript名称为`NPDMSPrintSansSC-Regular`。
- 上游版权和SIL Open Font License 1.1保留在[LICENSE](LICENSE)。字体数据只在服务端用于PDF嵌入。
- PDF库：[Apache PDFBox 3.0](https://pdfbox.apache.org/3.0/getting-started.html)，本模块锁定版本3.0.8。
