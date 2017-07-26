package info.deskchan.speech_command_system;

import info.deskchan.core.Plugin;
import info.deskchan.core.PluginProxyInterface;
import info.deskchan.core_utils.TextOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main implements Plugin {
    private static PluginProxyInterface pluginProxy;
    private String start_word="пожалуйста";

    private static final HashMap<String,Object> standartCommandsCoreQuery=new HashMap<String,Object>(){{
        put("eventName","speech:get");
    }};

    @Override
    public boolean initialize(PluginProxyInterface newPluginProxy) {
        pluginProxy=newPluginProxy;

        log("loading speech to command module");

        pluginProxy.sendMessage("core:add-event", TextOperations.toMap("tag: \"speech:get\""));

        pluginProxy.addMessageListener("DeskChan:user-said", (sender, tag, data) -> {
            String text=(String)((HashMap<String,Object>)data).getOrDefault("value","");
            ArrayList<String> words = PhraseComparison.toClearWords(text);
            if(PhraseComparison.Similar(words.get(0),start_word)<0.7) return;
            words.remove(0);
            pluginProxy.sendMessage("core:get-commands-match",standartCommandsCoreQuery,(s, d) -> {
                HashMap<String,Object> commands=(HashMap<String,Object>) d;
                commands=(HashMap<String,Object>)commands.get("commands");
                operateRequest(words,commands);
            });
        });

        log("loading speech to command module");
        return true;
    }

    void operateRequest(ArrayList<String> words,HashMap<String,Object> commandsInfo){
        float max_result=0;
        HashMap<String,Object> match_command_data=null;
        String match_command_name=null;
        boolean[] max_used=null;
        for(Map.Entry<String,Object> commandEntry : commandsInfo.entrySet()){
            HashMap<String,Object> command=(HashMap<String,Object>) commandEntry.getValue();
            String rule=(String) command.getOrDefault("rule",null);
            if(rule==null){
                pluginProxy.sendMessage(commandEntry.getKey(),new HashMap<String,Object>(){{
                    put("text",words);
                    if(command.containsKey("msgData"))
                        put("msgData",command.get("msgData"));
                }});
                continue;
            }
            ArrayList<String> rule_words = PhraseComparison.toClearWords(rule);
            boolean[] used=new boolean[words.size()];
            for(int i=0;i<words.size();i++) used[i]=false;
            float result=0;
            for(int k=0;k<rule_words.size();k++){
                float cur_res=0;
                int cur_pos=-1;
                for(int i=0;i<words.size();i++){
                    if(used[i]) continue;
                    float r=PhraseComparison.Similar(words.get(i),rule_words.get(k));
                    if(r>0.5 && r>cur_res){
                        cur_res=r;
                        cur_pos=i;
                    }
                }
                if(cur_pos<0) continue;
                result+=cur_res;
                used[cur_pos]=true;
            }
            result/=words.size();
            if(result>max_result){
                result=max_result;
                match_command_name=commandEntry.getKey();
                match_command_data=command;
                max_used=used;
            }
        }
        if(match_command_name!=null) {
            HashMap<String,Object> ret=new HashMap<>();
            for (int i = max_used.length - 1; i >= 0; i--) {
                if(max_used[i]) words.remove(i);
            }
            if(words.size()>0)
                ret.put("text",words);
            if(match_command_data.containsKey("msgData"))
                ret.put("msgData",match_command_data.get("msgData"));
            pluginProxy.sendMessage( match_command_name, ret);
        }
    }
    static void log(String text) {
        pluginProxy.log(text);
    }

    static void log(Throwable e) {
        pluginProxy.log(e);
    }
}
