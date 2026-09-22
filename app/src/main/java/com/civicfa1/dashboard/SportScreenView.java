package com.civicfa1.dashboard;

import android.content.Context;

final class SportScreenView extends RefLayout {
    interface Picker { void pick(int slot, SensorType current); }
    final TachometerView tach;
    final SensorCardView[] cards=new SensorCardView[5];
    private Picker picker;
    SportScreenView(Context c){super(c);setBackgroundColor(Ui.BG);tach=new TachometerView(c);addRef(tach,390,105,500,430);
        cards[0]=new SensorCardView(c,SensorType.COOLANT,Ui.CYAN);cards[1]=new SensorCardView(c,SensorType.MODULE_VOLTAGE,Ui.CYAN);cards[2]=new SensorCardView(c,SensorType.THROTTLE,Ui.GREEN);cards[3]=new SensorCardView(c,SensorType.LOAD,Ui.YELLOW);cards[4]=new SensorCardView(c,SensorType.INTAKE,Ui.CYAN);
        addRef(cards[0],25,145,365,185);addRef(cards[1],890,145,365,185);addRef(cards[2],45,350,350,165);addRef(cards[3],430,475,420,115);addRef(cards[4],885,350,350,165);
        for(int i=0;i<cards.length;i++){final int slot=i;cards[i].setOnClickListener(v->{if(picker!=null)picker.pick(slot,cards[slot].getSensor());});}
    }
    void setPicker(Picker p){picker=p;}
    void setSensor(int slot,SensorType t){if(slot>=0&&slot<cards.length)cards[slot].setSensor(t);}
    SensorType getSensor(int slot){return cards[slot].getSensor();}
    void update(ObdManager.State state,ObdManager.Telemetry t){boolean connected=state==ObdManager.State.ECU_CONNECTED;long now=System.currentTimeMillis();boolean rv=valid(SensorType.RPM,t,connected,now);boolean sv=valid(SensorType.SPEED,t,connected,now);tach.setData(t.rpm,rv,t.speed,sv);for(SensorCardView card:cards){SensorType s=card.getSensor();boolean unsupported=!s.supported(t);boolean ok=valid(s,t,connected,now)&&!unsupported;card.setReading(s.value(t),ok,unsupported);}}
    private static boolean valid(SensorType s,ObdManager.Telemetry t,boolean connected,long now){float v=s.value(t);long at=s.at(t);return connected&&!Float.isNaN(v)&&at>0&&now-at<=s.ttlMs();}
}
