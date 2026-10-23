package dev.sgs.core.sim;

/** Standalone developer runner: real core actions and verdict, no Minecraft or fake network proof. */
public final class HeadlessSimCli {
    public static void main(String[] args) {
        // Windows console encodings must not corrupt the core's Chinese card names in saved logs.
        System.setOut(new java.io.PrintStream(System.out,true,java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err,true,java.nio.charset.StandardCharsets.UTF_8));
        int players=Integer.parseInt(args.length>0?args[0]:"6");
        long seed=Long.parseLong(args.length>1?args[1]:"42");
        int ticks=Integer.parseInt(args.length>2?args[2]:"20000");
        if ((players!=6 && players!=8) || ticks<1 || ticks>1000000) throw new IllegalArgumentException("players=6/8; maxTicks=1..1000000");
        System.out.println("SGS_AI_SIM content=dev-119-20261017 heroes=3 network=false players="+players+" seed="+seed);
        var result=HeadlessSim.run(players,seed,ticks,System.out::println);
        System.out.println("SGS_AI_SIM_RESULT finished="+result.finished()+" outcome="+result.outcome()+" turns="+result.turns()+" steps="+result.ticks()+" error="+result.error());
        if (!result.finished() || result.error()!=null) throw new IllegalStateException("AI simulation did not finish: "+result.error());
    }
}
