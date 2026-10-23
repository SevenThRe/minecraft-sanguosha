package dev.sgs.core.game;

import dev.sgs.core.addon.AddonRuntime;
import dev.sgs.core.card.*;
import java.util.*;

/** Public, committed actions only. Never derives visibility from a later card location. */
public final class MatchJournal {
  public static final int MAX_LINES=512, MAX_CHARS=16000;
  private final GameState state;
  private final Deque<String> lines=new ArrayDeque<>();
  private int chars;
  private int drawSeat=-1,drawCount;
  public MatchJournal(GameState state){this.state=state;}
  public List<String> lines(){return List.copyOf(lines);}
  public void restore(List<String> values){lines.clear();chars=0;drawSeat=-1;drawCount=0;values.forEach(this::append);}
  public void add(String line){drawSeat=-1;drawCount=0;append("第"+Math.max(1,state.turnNumber())+"回合 · "+line);}
  private void append(String line){
    if(line.length()>4000){for(int start=0;start<line.length();start+=4000)append(line.substring(start,Math.min(line.length(),start+4000)));return;}
    lines.addLast(line);chars+=line.length();
    while(lines.size()>MAX_LINES || chars>MAX_CHARS)chars-=lines.removeFirst().length();
  }
  public String seat(int seat){
    if(seat<0 || seat>=state.players().size())return "牌堆";
    var player=state.players().get(seat);var hero=player.hero();
    String name=hero==null?"待选武将":AddonRuntime.current().present(List.of(hero.id())).stream().findFirst().map(dev.sgs.core.addon.HeroPresentation::name).orElse(hero.id());
    return (seat+1)+"号「"+name+"」";
  }
  public static String type(CardType type){return "【{card:"+type.id()+"}】";}
  public static String card(CardInstance card){return type(card.type())+switch(card.suit()){
    case SPADE->"♠";case HEART->"♥";case CLUB->"♣";case DIAMOND->"♦";case NONE->"";
  }+card.rank().label();}
  public void skill(int seat,String id){
    String name=AddonRuntime.current().skill(id).map(dev.sgs.core.addon.SkillPresentation::name).orElse("{skill:"+id+"}");
    add(seat(seat)+"发动了技能【"+name+"】");
  }
  public void targetedUse(int source,GameCard card,List<Integer> targets){
    String actor=seat(source);
    // Replace the immediately preceding physical-use line with its complete public relation.
    if(!card.sourceCards().isEmpty() && !lines.isEmpty() && lines.getLast().endsWith(actor+"使用了"+String.join("、",card.sourceCards().stream().map(MatchJournal::card).toList()))){chars-=lines.removeLast().length();}
    String detail=card.isVirtual()?type(card.type()):card(card.sourceCards().getFirst());
    add(actor+"对"+String.join("、",targets.stream().map(this::seat).toList())+"使用了"+detail);
  }
  public void movement(CardMove move){
    if(move.cards().isEmpty() || move.reason()==CardMove.Reason.SHUFFLE)return;
    if(move.publicCards() && move.cards().size()>16){
      for(int i=0;i<move.cards().size();i+=16)movement(new CardMove(move.sequence(),move.from(),move.to(),move.cards().subList(i,Math.min(i+16,move.cards().size())),move.reason(),move.actorSeat(),true));return;
    }
    if(move.reason()==CardMove.Reason.DRAW){
      int count=move.cards().size();
      if(drawSeat==move.to().seat()&&!lines.isEmpty()){count+=drawCount;chars-=lines.removeLast().length();}
      add(seat(move.to().seat())+"摸了"+count+"张牌");drawSeat=move.to().seat();drawCount=count;return;
    }
    String detail=move.publicCards()?String.join("、",move.cards().stream().map(MatchJournal::card).toList()):move.cards().size()+"张牌";
    String actor=seat(move.actorSeat()),from=seat(move.from().seat()),to=seat(move.to().seat());
    String text=switch(move.reason()){
      case DRAW -> to+"摸了"+move.cards().size()+"张牌";
      case USE -> actor+"使用了"+detail;
      case RESPONSE -> actor+"打出了"+detail;
      case FINISH -> "结算后，"+detail+"进入弃牌堆";
      case EQUIP -> to+"装备了"+detail;
      case EQUIPMENT_REPLACED -> from+"替换装备，弃置"+detail;
      case DELAYED_USE -> actor+"将"+detail+"置入"+to+"的判定区";
      case DELAY_PASS -> detail+"从"+from+"的判定区移至"+to+"的判定区";
      case JUDGMENT_REVEAL -> actor+"的判定牌："+detail;
      case JUDGMENT_REPLACE -> actor+"替换判定牌："+detail;
      case REVEAL -> actor+(move.publicCards()?"展示了":"查看了")+detail;
      case EFFECT_GAIN, SKILL_GIVE -> move.to().equals(CardZone.DRAW)?actor+"将"+detail+"放回牌堆":to+"从"+zone(move.from())+"获得了"+detail;
      case DISCARD -> actor+"弃置了"+(move.from().seat()!=move.actorSeat()?from+"的":"")+detail;
      case DEATH -> from+"阵亡，弃置"+detail;
      case RECAST -> actor+"重铸了"+detail;
      default -> null;
    };
    if(text!=null)add(text);
  }
  private String zone(CardZone zone){return switch(zone.kind()){
    case DRAW->"牌堆";case DISCARD->"弃牌堆";case PROCESSING->"处理区";
    case HAND->seat(zone.seat())+"的手牌区";case EQUIPMENT->seat(zone.seat())+"的装备区";
    case JUDGMENT->seat(zone.seat())+"的判定区";case SPECIAL->seat(zone.seat())+"的武将牌区";
  };}
}
