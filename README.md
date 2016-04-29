# Send To Terminal

Port of Sublime Text's [SendText plugin](https://github.com/wch/SendText).

This package sends text to a terminal (or other program). If text is selected, it will send the selection to the
  terminal; if no text is selected, it will send the current line to the terminal and move the cursor to the next line.
  This is very useful for coding in interpreted languages.

Send To Terminal presently works with:

* Terminal.app on Mac OS X. Send To Terminal will send the text to the most recently active Terminal window.
* iTerm on Mac OS X. Send To Terminal will send the text to the most recently iTerm window.
* GNU `screen` on any platform (Linux and Mac OS X). Screen is a terminal multiplexer which you can start in any
    terminal emulator. Send To Terminal will send the text to the most recently active `screen` session.
* `tmux` on any platform (Linux and Mac OS X). tmux is a terminal multiplexer (like GNU screen) which you can start
    in any terminal emulator. Send To Terminal will send the text to the most recently active `tmux` session.

Select the destination you would like to use in *Settings&rarr;Tools&rarr;Send To Terminal*

## Installation

Currently the plugin is not published anywhere. Install it by cloning the repo, and then:

```
./gradlew clean buildPlugin
```

In IntelliJ, use *Settings&rarr;Plugins&rarr;Install from disk...* and select `build/distributions/SendToTerminal-1.0.0-SNAPSHOT.zip`.

The plugin installs a new action called "Send To Terminal". The action does not have an initial keybinding, you'll have to map it.

## TODO

* screen and tmux are completely untested.
* Many complicated cases (multiple cursors etc) are completely untested.
* This should also work with the built-in terminal.
* The original contains a workaround when sending more than 2k characters to `screen`. I didn't implement this, because I'm lazy.
* It would be nice if language extensions could allow selection of semantic forms rather than having it be line based.
