package app.cadence.build;

import com.android.build.api.instrumentation.*;
import org.objectweb.asm.*;

/** Backports NewPipe's Java 10 URL codec overloads, which Android exposes only from API 33. */
public abstract class UrlCodecVisitor implements AsmClassVisitorFactory<InstrumentationParameters.None> {
  @Override public boolean isInstrumentable(ClassData data) {
    return data.getClassName().startsWith("org.schabi.newpipe.extractor.");
  }
  @Override public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
    return new ClassVisitor(Opcodes.ASM9, next) {
      @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
          @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC
                && ((owner.equals("java/net/URLDecoder") && name.equals("decode"))
                    || (owner.equals("java/net/URLEncoder") && name.equals("encode")))
                && descriptor.equals("(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;"))
              owner = "app/cadence/music/UrlCodecCompat";
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
          }
        };
      }
    };
  }
}
