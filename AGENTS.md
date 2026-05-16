1. 请不要交付无法通过编译或有明显问题的代码，修改完成后Debug打包。
2. 你可以从 @docs/MANUAL.md 中获取项目相关信息。
3. 开发过程中，留意 @docs/MANUAL.md 的维护。涉及项目级别变动，务必做好记录，方便后续会话丢失重要记忆。
4. 涉及 UI、交互、遥控器焦点、页面布局、视觉样式、设置页结构、播放页/浏览页行为变更时，务必先读取并同步维护根目录 @DESIGN.md。该文件作为当前 UI/交互设计规范，格式遵循 https://github.com/google-labs-code/design.md。
5. 设计样式时必须优先使用 @DESIGN.md YAML front matter 和正文中注明的公共样式 token：颜色使用 @color/ui_*，尺寸/圆角/描边/字号使用 @dimen/ui_*，可复用文本和按钮样式使用 @style/Tsm*，背景优先复用 @drawable/bg_*。除图标、launcher、一次性插画或确有说明的特殊尺寸外，不要在 XML 中新增通用硬编码颜色、圆角、描边、字号。
6. 需要引用设计规范时统一使用 @DESIGN.md。
7. 提交日志需要包含规范前缀、简要描述和改动重点，例如 `feat: 增加图片全屏预览`。
