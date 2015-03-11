package frontEnd;

import globalListener.GlobalKeyListener;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.swing.SwingUtilities;

import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;

import utilities.CodeConverter;
import utilities.Function;
import core.Config;
import core.Core;
import core.DynamicCompiler;
import core.DynamicJavaCompiler;
import core.DynamicPythonCompiler;
import core.Recorder;
import core.UserDefinedAction;

public class BackEndHolder {

	protected ScheduledThreadPoolExecutor executor;
	protected ScheduledFuture<?> mouseTracker;
	protected Thread compiledExecutor;

	protected Core core;
	protected Recorder recorder;

	protected final DynamicJavaCompiler javaCompiler;
	protected final DynamicPythonCompiler pythonCompiler;
	protected UserDefinedAction customFunction;

	protected boolean isRecording, isReplaying, isRunning;

	private final Main main;

	public BackEndHolder(Main main) {
		this.main = main;

		executor = new ScheduledThreadPoolExecutor(10);
		core = new Core();
		recorder = new Recorder(core);

		javaCompiler = new DynamicJavaCompiler("CustomAction", new String[]{"core"}, new String[]{});
		pythonCompiler = new DynamicPythonCompiler();
	}

	protected void startGlobalHotkey() throws NativeHookException {
		GlobalKeyListener keyListener = new GlobalKeyListener();
		keyListener.setKeyPressed(new Function<NativeKeyEvent, Boolean>() {
			@Override
			public Boolean apply(NativeKeyEvent r) {
				if (!main.hotkey.isVisible()) {
					if (CodeConverter.getKeyEventCode(r.getKeyCode()) == Config.RECORD) {
						switchRecord();
					} else if (CodeConverter.getKeyEventCode(r.getKeyCode()) == Config.REPLAY) {
						switchReplay();
					} else if (CodeConverter.getKeyEventCode(r.getKeyCode()) == Config.COMPILED_REPLAY) {
						switchRunningCompiledAction();
					}
				}
				return true;
			}
		});
		keyListener.startListening();
	}

	protected DynamicCompiler getCompiler() {
		if (main.rbmiCompileJava.isSelected()) {
			return javaCompiler;
		} else if (main.rbmiCompilePython.isSelected()) {
			return pythonCompiler;
		} else {
			return null;
		}
	}

	protected void switchRecord() {
		if (!isRecording) {//Start record
			recorder.clear();
			recorder.record();
			isRecording = true;
			main.bRecord.setText("Stop");

			setEnableReplay(false);
		} else {//Stop record
			recorder.stopRecord();
			isRecording = false;
			main.bRecord.setText("Record");

			setEnableReplay(true);
		}
	}

	protected void switchReplay() {
		if (isReplaying) {
			isReplaying = false;
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					main.bReplay.setText("Replay");
					setEnableRecord(true);
				}
			});
			recorder.stopReplay();
		} else {
			isReplaying = true;
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					main.bReplay.setText("Stop replay");
					setEnableRecord(false);
				}
			});
			recorder.replay(new Function<Void, Void>() {
				@Override
				public Void apply(Void r) {
					switchReplay();
					return null;
				}
			}, 5, false);
		}

	}

	protected void switchRunningCompiledAction() {
		if (isRunning) {
			isRunning = false;
			if (compiledExecutor != null) {
				while (compiledExecutor.isAlive()) {
					compiledExecutor.interrupt();
				}
			}

			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					main.bRun.setText("Run Compiled Action");
				}
			});
		} else {
			isRunning = true;

			compiledExecutor = new Thread(new Runnable() {
			    @Override
				public void run() {
			    	try {
						customFunction.action(core);
					} catch (InterruptedException e) {//Stopped prematurely
						return;
					}

					switchRunningCompiledAction();
			    }
			});
			compiledExecutor.start();

			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					main.bRun.setText("Stop running");
				}
			});
		}
	}

	protected void promptSource() {
		StringBuffer sb = new StringBuffer();
		if (main.rbmiCompileJava.isSelected()) {
			sb.append("package core;\n");
			sb.append("import core.UserDefinedAction;\n");

			sb.append("public class CustomAction extends UserDefinedAction {\n");
			sb.append("    public void action(final Core controller) throws InterruptedException {\n");
			sb.append("        System.out.println(\"hello\");\n");
			sb.append("        controller.mouse().move(0, 0);\n");
			sb.append("        controller.mouse().moveBy(300, 200);\n");
			sb.append("        controller.mouse().moveBy(-200, 200);\n");
			sb.append("        controller.blockingWait(1000);\n");
			sb.append("    }\n");
			sb.append("}");
		} else if (main.rbmiCompilePython.isSelected()) {
			sb.append("import repeat_lib\n");
			sb.append("if __name__ == \"__main__\":\n");
			sb.append("    print \"Hello\"\n");
			sb.append("    repeat_lib.mouseMoveBy(100, 0)");
		}
		main.taSource.setText(sb.toString());
	}

	private void setEnableRecord(boolean state) {
		main.bRecord.setEnabled(state);
	}

	private void setEnableReplay(boolean state) {
		main.bReplay.setEnabled(state);
		main.tfRepeatCount.setEnabled(state);
		main.tfRepeatDelay.setEnabled(state);

		if (state) {
			main.tfRepeatCount.setText("1");
			main.tfRepeatDelay.setText("0");
		}
	}
}