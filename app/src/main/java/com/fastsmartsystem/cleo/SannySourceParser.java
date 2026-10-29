package com.fastsmartsystem.cleo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Small Sanny Builder-style control-flow preprocessor for the existing CLEO compiler.
 * Supports: if [and|or], condition opcode lines, then, else, end.
 * Existing raw opcode scripts pass through unchanged when no high-level keywords are used.
 */
public final class SannySourceParser {
    private static final Pattern IF_LINE = Pattern.compile("(?i)^if(?:\\s+(?:and|or))?$");
    private static final Pattern WHILE_LINE = Pattern.compile("(?i)^while\\s+([0-9a-f]{4}\\s*:\\s*.+)$");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)^(\\d+@|[$]\\w+)\\s*=\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final Pattern COMPOUND_ASSIGNMENT = Pattern.compile("(?i)^((?:\\d+@|[$]\\w+))\\s*(\\+=|-=|\\*=|/=)\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final Pattern REVERSED_COMPARISON = Pattern.compile("(?i)^(-?\\d+(?:\\.\\d+)?)\\s*(>=|<=|>|<)\\s*((?:\\d+@|[$]\\w+))$");
    private static final Pattern COMPARISON = Pattern.compile("(?i)^(\\d+@|[$]\\w+)\\s*(==|!=|>=|<=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final Pattern JF_WITH_OPCODE = Pattern.compile("(?i)^(\\s*004D\\s*:\\s*)jf(\\s+.*|\\s*)$");
    private static final Pattern JF_WITHOUT_OPCODE = Pattern.compile("(?i)^\\s*jf(\\s+.*|\\s*)$");
    private static final Pattern JUMP_ALIAS = Pattern.compile("(?i)^\\s*(?:goto|jump)\\s+(@[A-Za-z0-9_]+)\\s*$");
    private static final Pattern GOTO_IF_FALSE = Pattern.compile("(?i)^\\s*goto_if_false\\s+(@[A-Za-z0-9_]+)\\s*$");
    private static final Pattern ALLOC = Pattern.compile("(?i)^\\s*Alloc\\s*\\(\\s*\\$([A-Za-z_]\\w*)\\s*,\\s*(\\d+)\\s*\\)\\s*$");
    private static final Pattern GOSUB_ALIAS = Pattern.compile("(?i)^\\s*gosub\\s+(@[A-Za-z0-9_]+)\\s*$");
    private static final Pattern RETURN_ALIAS = Pattern.compile("(?i)^\\s*return\\s*$");
    private static final Pattern TERMINATE_ALIAS = Pattern.compile("(?i)^\\s*(?:terminate_this_script|end_thread)\\s*$");
    private static final Pattern TERMINATE_CUSTOM_ALIAS = Pattern.compile("(?i)^\\s*terminate_this_custom_script\\s*$");
    private static final Pattern SCRIPT_NAME = Pattern.compile("(?i)^\\s*(?:script_name|thread)\\s+([\"\'])(.*?)\\1\\s*$");
    private static final Pattern NOT_COMPARISON = Pattern.compile("(?i)^not\\s+((?:\\d+@|[$]\\w+))\\s*(==|!=|>=|<=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final Pattern WAIT_ALIAS = Pattern.compile("(?i)^\\s*wait\\s+(-?\\d+)\\s*$");
    private static final class Block {
        final String falseLabel;
        final String endLabel;
        final boolean whileBlock;
        boolean hasElse;
        boolean waitingForThen = true;
        Block(String falseLabel, String endLabel) { this(falseLabel, endLabel, false); }
        Block(String falseLabel, String endLabel, boolean whileBlock) {
            this.falseLabel = falseLabel; this.endLabel = endLabel; this.whileBlock = whileBlock;
            this.waitingForThen = !whileBlock;
        }
    }

    private SannySourceParser() {}

    public static String parse(String source) {
        String[] lines = source.split("\\r?\\n", -1);
        StringBuilder out = new StringBuilder(source.length() + 128);
        Deque<Block> blocks = new ArrayDeque<>();
        int serial = 0;
        boolean inVarDeclaration = false;
        boolean inHexBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            String lower = line.toLowerCase(Locale.ROOT);

            // Keep hex blocks intact for ScriptCompiler's raw-byte handler.
            if (inHexBlock) {
                append(out, raw);
                if (lower.equals("end")) inHexBlock = false;
                continue;
            }
            if (lower.equals("hex")) {
                inHexBlock = true;
                append(out, raw);
                continue;
            }
            // Variable declarations are metadata. Alloc() is converted to the compiler's
            // existing {$VAR name index} declaration format; var...end type blocks are skipped.
            if (inVarDeclaration) {
                if (lower.equals("end")) inVarDeclaration = false;
                continue;
            }
            if (lower.equals("var")) {
                inVarDeclaration = true;
                continue;
            }
            Matcher alloc = ALLOC.matcher(line);
            if (alloc.matches()) {
                append(out, "{$VAR " + alloc.group(1) + " " + alloc.group(2) + "}");
                continue;
            }

            // Accept the common Sanny Builder shorthand "jf" as an alias for
            // "jump_if_false", both with an explicit opcode and as a bare mnemonic.
            Matcher jfWithOpcode = JF_WITH_OPCODE.matcher(raw);
            Matcher jfWithoutOpcode = JF_WITHOUT_OPCODE.matcher(line);
            if (jfWithOpcode.matches()) {
                append(out, jfWithOpcode.group(1) + "jump_if_false" + jfWithOpcode.group(2));
                continue;
            }
            if (jfWithoutOpcode.matches()) {
                append(out, "004D: jump_if_false" + jfWithoutOpcode.group(1));
                continue;
            }

            // goto_if_false is an alias of jf / jump_if_false (opcode 004D).
            Matcher gotoIfFalse = GOTO_IF_FALSE.matcher(line);
            if (gotoIfFalse.matches()) {
                append(out, "004D: jump_if_false " + gotoIfFalse.group(1));
                continue;
            }

            // Control-flow aliases accepted without an explicit opcode number.
            // GTA SA SCM opcodes: 0002 jump, 0050 gosub, 0051 return.
            Matcher jumpAlias = JUMP_ALIAS.matcher(line);
            if (jumpAlias.matches()) {
                append(out, "0002: jump " + jumpAlias.group(1));
                continue;
            }
            Matcher gosubAlias = GOSUB_ALIAS.matcher(line);
            if (gosubAlias.matches()) {
                append(out, "0050: gosub " + gosubAlias.group(1));
                continue;
            }
            if (RETURN_ALIAS.matcher(line).matches()) {
                append(out, "0051: return");
                continue;
            }
            if (TERMINATE_CUSTOM_ALIAS.matcher(line).matches()) {
                append(out, "004E: terminate_this_script");
                continue;
            }
            if (TERMINATE_ALIAS.matcher(line).matches()) {
                append(out, "004E: terminate_this_script");
                continue;
            }
            Matcher scriptName = SCRIPT_NAME.matcher(line);
            if (scriptName.matches()) {
                append(out, "03A4: name_thread " + scriptName.group(1) + scriptName.group(2) + scriptName.group(1));
                continue;
            }
            Matcher notComparison = NOT_COMPARISON.matcher(line);
            if (notComparison.matches()) {
                String op = notComparison.group(2).equals("!=") ? "<>" : notComparison.group(2);
                append(out, "0039: not " + notComparison.group(1) + " " + op + " " + notComparison.group(3));
                continue;
            }
            Matcher waitAlias = WAIT_ALIAS.matcher(line);
            if (waitAlias.matches()) {
                append(out, "0001: wait " + waitAlias.group(1));
                continue;
            }

            Matcher whileMatcher = WHILE_LINE.matcher(line);
            if (whileMatcher.matches()) {
                String start = "__SB_WHILE_START_" + serial;
                String exit = "__SB_WHILE_EXIT_" + serial;
                append(out, ":" + start);
                append(out, "00D6: if");
                append(out, whileMatcher.group(1));
                append(out, "004D: jump_if_false @" + exit);
                blocks.push(new Block(exit, start, true));
                serial++;
                continue;
            }
            Matcher compound = COMPOUND_ASSIGNMENT.matcher(line);
            if (compound.matches()) {
                String operator = compound.group(2);
                boolean decimal = compound.group(3).contains(".");
                String opcode;
                if (operator.equals("+=")) opcode = decimal ? "0009" : "000A";
                else if (operator.equals("-=")) opcode = decimal ? "000D" : "000C";
                else if (operator.equals("*=")) opcode = decimal ? "0011" : "0010";
                else opcode = decimal ? "0015" : "0014";
                append(out, opcode + ": " + compound.group(1) + " " + operator + " " + compound.group(3));
                continue;
            }
            Matcher reversedComparison = REVERSED_COMPARISON.matcher(line);
            if (reversedComparison.matches()) {
                String op = reversedComparison.group(2);
                if (op.equals(">=")) op = "<=";
                else if (op.equals("<=")) op = ">=";
                else if (op.equals(">")) op = "<";
                else op = ">";
                append(out, "0039: " + reversedComparison.group(3) + " " + op + " " + reversedComparison.group(1));
                continue;
            }
            Matcher assignment = ASSIGNMENT.matcher(line);
            if (assignment.matches()) {
                append(out, "0006: " + assignment.group(1) + " = " + assignment.group(2));
                continue;
            }
            Matcher comparison = COMPARISON.matcher(line);
            if (comparison.matches()) {
                String op = comparison.group(2);
                if (op.equals("==")) op = "==";
                else if (op.equals("!=")) op = "<>";
                append(out, "0039: " + comparison.group(1) + " " + op + " " + comparison.group(3));
                continue;
            }

            if (IF_LINE.matcher(line).matches()) {
                // A bare "if" followed by a condition and "jf" is normal Sanny syntax,
                // not the high-level if/then/end block form. Only create a block when a
                // matching then exists ahead.
                boolean hasThen = false;
                for (int j = i + 1; j < lines.length; j++) {
                    String look = lines[j].trim().toLowerCase(Locale.ROOT);
                    if (look.equals("then")) { hasThen = true; break; }
                    // A raw SCM condition block is closed by jf/jump_if_false,
                    // not by the high-level Sanny Builder "then" keyword. Stop
                    // look-ahead here so an unrelated later "then" cannot cause
                    // this raw "if and" to be pushed onto the control-flow stack.
                    if (IF_LINE.matcher(look).matches() || look.equals("end") || look.equals("else")
                            || JF_WITHOUT_OPCODE.matcher(look).matches()
                            || look.matches("(?i)^\\s*(?:004d\\s*:\\s*)?jump_if_false\\b.*")) {
                        break;
                    }
                }
                if (!hasThen) {
                    append(out, "00D6: if");
                    continue;
                }
                String logic = "";
                if (lower.endsWith(" and")) logic = " and";
                else if (lower.endsWith(" or")) logic = " or";
                append(out, "00D6: if" + logic);
                blocks.push(new Block("__SB_FALSE_" + serial, "__SB_END_" + serial));
                serial++;
                continue;
            }
            if (lower.equals("then")) {
                if (blocks.isEmpty() || !blocks.peek().waitingForThen) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": 'then' without matching 'if'");
                }
                Block b = blocks.peek();
                b.waitingForThen = false;
                append(out, "004D: jump_if_false @" + b.falseLabel);
                continue;
            }
            if (lower.equals("else")) {
                if (blocks.isEmpty()) throw new IllegalArgumentException("Line " + (i + 1) + ": 'else' without matching 'if'");
                Block b = blocks.peek();
                if (b.waitingForThen) throw new IllegalArgumentException("Line " + (i + 1) + ": expected 'then' before 'else'");
                if (b.hasElse) throw new IllegalArgumentException("Line " + (i + 1) + ": duplicate 'else'");
                append(out, "0002: jump @" + b.endLabel);
                append(out, ":" + b.falseLabel);
                b.hasElse = true;
                continue;
            }
            if (lower.equals("end")) {
                if (blocks.isEmpty()) throw new IllegalArgumentException("Line " + (i + 1) + ": 'end' without matching 'if'");
                Block b = blocks.pop();
                if (b.whileBlock) {
                    append(out, "0002: jump @" + b.endLabel);
                    append(out, ":" + b.falseLabel);
                    continue;
                }
                if (b.waitingForThen) throw new IllegalArgumentException("Line " + (i + 1) + ": expected 'then' after 'if'");
                append(out, ":" + (b.hasElse ? b.endLabel : b.falseLabel));
                continue;
            }
            append(out, raw);
        }
        if (!blocks.isEmpty()) throw new IllegalArgumentException("Unclosed control-flow block: missing 'end'");
        return out.toString();
    }

    private static void append(StringBuilder out, String line) {
        out.append(line).append('\n');
    }
}
