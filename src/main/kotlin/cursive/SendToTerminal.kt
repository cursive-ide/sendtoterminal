package cursive

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.xmlb.XmlSerializerUtil
import net.miginfocom.swing.MigLayout
import java.io.File
import javax.swing.ButtonGroup
import javax.swing.JPanel

/**
 * @author Colin Fleming
 */
class SendToTerminalAction : EditorAction(Handler()) {
  private class Handler : EditorWriteActionHandler(true) {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
      val project = editor.project ?: return
      val document = editor.document

      if (caret?.hasSelection() ?: editor.selectionModel.hasSelection()) {
        val start = caret?.selectionStart ?: editor.selectionModel.selectionStart
        val end = caret?.selectionEnd ?: editor.selectionModel.selectionEnd
        val s = document.charsSequence.subSequence(start, end).toString()
        sendText(project, s)
      } else {
        val position = caret?.visualPosition ?: editor.caretModel.visualPosition
        val lines = EditorUtil.calcSurroundingRange(editor, position, position)
        val lineStart = lines.first
        val nextLineStart = lines.second
        val start = editor.logicalPositionToOffset(lineStart)
        val end = editor.logicalPositionToOffset(nextLineStart)
        if (end <= start) {
          return
        }
        var s = document.charsSequence.subSequence(start, end).toString()
        sendText(project, s)

        if (caret != null)
          caret.moveToOffset(end)
        else
          editor.caretModel.moveToOffset(end)

        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
      }
    }

    public override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
      return !editor.isOneLineMode || editor.selectionModel.hasSelection()
    }

    private fun sendText(project: Project, text: String) {
      val settings = SendToTerminalSettings.getInstance()
      when (Terminal.valueOf(settings.terminal)) {
        Terminal.TerminalApp -> {
          val command = escape(StringUtil.trimEnd(text, "\n"))
          execute(project, "osascript", "-e", "tell app \"Terminal\" to do script \"$command\" in window 1")
        }
        Terminal.ITerm -> {
          val trimmed = StringUtil.trimEnd(text, "\n")
          val command = escape(if (trimmed.endsWith(" ")) trimmed + "\n" else trimmed)
          execute(project, "osascript",
                  "-e", "tell app \"iTerm\"",
                  "-e", "set mysession to current session of current terminal",
                  "-e", "tell mysession to write text \"$command\"",
                  "-e", "end tell")
        }
        Terminal.Tmux -> {
          execute(project, settings.tmuxPath, "set-buffer", text)
          execute(project, settings.tmuxPath, "paste-buffer", "-d")
        }
        Terminal.Screen -> {
          execute(project, settings.screenPath, "-X", "stuff", text)
        }
      }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun execute(project: Project, vararg args: String) {
      val builder = ProcessBuilder(args.asList())
      builder.directory(File(project.basePath))
      builder.redirectErrorStream(true)
      val process = builder.start()
      val stdout = FileUtil.loadTextAndClose(process.inputStream)
      process.waitFor()
      if (process.exitValue() != 0) {
        val message = "Process failed with exit code ${process.exitValue()}:\n$stdout"
        val notification = Notification("Send To Terminal", "Process failed", message, NotificationType.ERROR)
        Notifications.Bus.notify(notification, project)
      }
    }
  }

  override fun update(editor: Editor, presentation: Presentation, dataContext: DataContext) {
    super.update(editor, presentation, dataContext)
    if (editor.selectionModel.hasSelection()) {
      presentation.setText("Send Block to Terminal", true)
    } else {
      presentation.setText("Send Line to Terminal", true)
    }
  }
}

enum class Terminal { TerminalApp, ITerm, Tmux, Screen }

@State(name = "SendToTerminalSettings",
       storages = arrayOf(Storage(id = "dir",
                                  file = "${StoragePathMacros.APP_CONFIG}/SendToTerminal.xml",
                                  scheme = StorageScheme.DIRECTORY_BASED)))
class SendToTerminalSettings : PersistentStateComponent<SendToTerminalSettings> {
  var terminal: String = if (SystemInfo.isMac) Terminal.TerminalApp.name else Terminal.Tmux.name
  var tmuxPath: String = "tmux"
  var screenPath: String = "screen"

  override fun getState() = this

  override fun loadState(settings: SendToTerminalSettings) {
    XmlSerializerUtil.copyBean(settings, this)
  }

  companion object {
    fun getInstance(): SendToTerminalSettings = ServiceManager.getService(SendToTerminalSettings::class.java)
  }
}

class SendToTerminalConfigurable : Configurable {
  val panel = JPanel(MigLayout("wrap 2, insets 0", "[::pref][fill]"))

  val terminalApp = JBRadioButton("Terminal.app")
  val iTerm = JBRadioButton("iTerm")
  val tmux = JBRadioButton("tmux")
  val tmuxPath = TextFieldWithBrowseButton()
  val screen = JBRadioButton("screen")
  val screenPath = TextFieldWithBrowseButton()
  val group = ButtonGroup()

  init {
    group.add(terminalApp)
    group.add(iTerm)
    group.add(tmux)
    group.add(screen)

    tmuxPath.addBrowseFolderListener("Locate tmux",
                                     "Select the tmux executable.",
                                     null,
                                     FileChooserDescriptor(true, false, false, false, false, false))
    screenPath.addBrowseFolderListener("Locate screen",
                                       "Select the screen executable.",
                                       null,
                                       FileChooserDescriptor(true, false, false, false, false, false))

    terminalApp.addActionListener({ update() })
    iTerm.addActionListener({ update() })
    tmux.addActionListener({ update() })
    screen.addActionListener({ update() })

    if (SystemInfo.isMac) {
      panel.add(terminalApp, "span 2")
      panel.add(iTerm, "span 2")
    }

    panel.add(tmux, "span 2")
    panel.add(JBLabel("tmux executable:"))
    panel.add(tmuxPath)
    panel.add(screen, "span 2")
    panel.add(JBLabel("screen executable:"))
    panel.add(screenPath)
  }

  private fun update() {
    tmuxPath.isEnabled = tmux.isSelected
    screenPath.isEnabled = screen.isSelected
  }

  private fun selectedTerminal() =
      if (terminalApp.isSelected) Terminal.TerminalApp
      else if (iTerm.isSelected) Terminal.ITerm
      else if (tmux.isSelected) Terminal.Tmux
      else Terminal.Screen

  override fun getDisplayName() = "Send To Terminal"

  override fun createComponent() = panel

  override fun isModified(): Boolean {
    val settings = SendToTerminalSettings.getInstance()
    return settings.terminal != selectedTerminal().name ||
           settings.tmuxPath != tmuxPath.text ||
           settings.screenPath != screenPath.text
  }

  override fun apply() {
    val settings = SendToTerminalSettings.getInstance()
    settings.terminal = selectedTerminal().name
    settings.tmuxPath = tmuxPath.text
    settings.screenPath = screenPath.text
  }

  override fun reset() {
    val settings = SendToTerminalSettings.getInstance()
    terminalApp.isSelected = settings.terminal == Terminal.TerminalApp.name
    iTerm.isSelected = settings.terminal == Terminal.ITerm.name
    tmux.isSelected = settings.terminal == Terminal.Tmux.name
    screen.isSelected = settings.terminal == Terminal.Screen.name
    tmuxPath.text = settings.tmuxPath
    screenPath.text = settings.screenPath
    update()
  }

  override fun disposeUIResources() {
  }

  override fun getHelpTopic() = null
}
