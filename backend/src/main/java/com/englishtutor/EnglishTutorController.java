package com.englishtutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EnglishTutorController {

    @Autowired
    private LLMService llmService;

    // 鍦烘櫙鍒楄〃
    @GetMapping("/scenes")
    public List<Map<String, String>> scenes() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(Map.of("id", "interview", "name", "姹傝亴闈㈣瘯", "desc", "妯℃嫙 job interview 瀵硅瘽缁冧範"));
        list.add(Map.of("id", "restaurant", "name", "椁愬巺鐐归", "desc", "Restaurant ordering and dining"));
        list.add(Map.of("id", "meeting", "name", "鍟嗗姟浼氳", "desc", "Business meeting in English"));
        list.add(Map.of("id", "travel", "name", "鏃呰闂矾", "desc", "Asking for directions while traveling"));
        list.add(Map.of("id", "shopping", "name", "璐墿浜ゆ祦", "desc", "Shopping and bargaining in English"));
        return list;
    }

    // 瀵硅瘽鎺ュ彛
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> req) {
        String scene = req.getOrDefault("scene", "interview");
        String message = req.getOrDefault("message", "");
        String history = req.getOrDefault("history", "");

        String prompt = buildChatPrompt(scene, message, history);
        String reply = llmService.chat(prompt);

        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        return result;
    }

    // 璇勬祴鎺ュ彛锛堣闊宠浆鏂囧瓧鍚庣殑璇勬祴锛?    @PostMapping("/evaluate")
    public Map<String, String> evaluate(@RequestBody Map<String, String> req) {
        String text = req.getOrDefault("text", "");
        String scene = req.getOrDefault("scene", "interview");

        String prompt = "浣犳槸涓€涓嫳璇暀甯堛€傝璇勬祴鐢ㄦ埛鍦ㄤ笅杩板満鏅腑鐨勮嫳璇〃杈俱€俓n\n" +
                "鍦烘櫙锛? + scene + "\n" +
                "鐢ㄦ埛璇达細" + text + "\n\n" +
                "璇蜂粠浠ヤ笅缁村害璇勫垎锛?-100锛夛細\n" +
                "1. 璇硶姝ｇ‘鎬n" +
                "2. 璇嶆眹閫傚綋鎬n" +
                "3. 鍙戦煶娴佺晠搴︼紙鏍规嵁鏂囧瓧鍒ゆ柇锛塡n" +
                "4. 琛ㄨ揪鑷劧搴n\n" +
                "鐒跺悗缁欏嚭2-3涓籂閿欏缓璁€俓n\n" +
                "杈撳嚭鏍煎紡锛歕n" +
                "璇硶锛?5鍒?| 璇嶆眹锛?0鍒?| 娴佺晠搴︼細75鍒?| 鑷劧搴︼細80鍒哱n" +
                "寤鸿锛?. ... 2. ... 3. ...";

        String result = llmService.chat(prompt);
        Map<String, String> res = new HashMap<>();
        res.put("evaluation", result);
        return res;
    }

    // 璇惧悗鎬荤粨
    @PostMapping("/summary")
    public Map<String, String> summary(@RequestBody Map<String, String> req) {
        String history = req.getOrDefault("history", "");

        String prompt = "浣犳槸涓€涓嫳璇暀甯堛€傛牴鎹互涓嬪璇濊褰曪紝鐢熸垚涓€浠借鍚庡涔犳€荤粨锛歕n\n" +
                history + "\n\n" +
                "鎬荤粨瑕佸寘鎷細\n" +
                "1. 鏈缁冧範鐨勫満鏅痋n" +
                "2. 鐢ㄦ埛琛ㄧ幇濂界殑鍦版柟锛堝垪鍑哄叿浣撲緥瀛愶級\n" +
                "3. 闇€瑕佹敼杩涚殑鍦版柟锛堣娉?璇嶆眹/琛ㄨ揪鏂瑰紡锛塡n" +
                "4. 涓嬫缁冧範寤鸿锛?-2鏉★級\n\n" +
                "鐢ㄤ腑鏂囧啓锛岄潰鍚戝鐢熺敤鎴枫€?;

        String result = llmService.chat(prompt);
        Map<String, String> res = new HashMap<>();
        res.put("summary", result);
        return res;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    private String buildChatPrompt(String scene, String message, String history) {
        String sceneDesc;
        if ("interview".equals(scene)) {
            sceneDesc = "姹傝亴闈㈣瘯鍦烘櫙銆備綘鎵紨闈㈣瘯瀹橈紝鎻愰棶涓撲笟涓旀湁鎸戞垬鎬х殑闂銆?;
        } else if ("restaurant".equals(scene)) {
            sceneDesc = "椁愬巺鐐归鍦烘櫙銆備綘鎵紨鏈嶅姟鍛橈紝甯姪椤惧瀹屾垚鐐归銆?;
        } else if ("meeting".equals(scene)) {
            sceneDesc = "鍟嗗姟浼氳鍦烘櫙銆備綘鎵紨浼氳涓绘寔浜猴紝寮曞璁ㄨ銆?;
        } else if ("travel".equals(scene)) {
            sceneDesc = "鏃呰闂矾鍦烘櫙銆備綘鎵紨褰撳湴浜猴紝甯姪娓稿銆?;
        } else if ("shopping".equals(scene)) {
            sceneDesc = "璐墿浜ゆ祦鍦烘櫙銆備綘鎵紨搴楀憳锛屼笌椤惧娌熼€氶渶姹傘€?;
        } else {
            sceneDesc = "鏃ュ父鑻辫瀵硅瘽缁冧範銆?;
        }

        String systemPrompt = "浣犳槸AI鑻辫鍙ｈ闄粌銆? + sceneDesc +
                "\n瑕佹眰锛歕n" +
                "1. 鐢ㄦ埛鐢ㄨ嫳璇锛屼綘鐢ㄨ嫳璇洖澶嶏紙鍋跺皵鍙互鐢ㄤ腑鏂囨彁绀猴級\n" +
                "2. 姣忚疆瀵硅瘽鍚庯紝缁欏嚭褰撳墠鍥炲鐨勫湴閬撶▼搴﹁瘎鍒嗭紙1-10鍒嗭級\n" +
                "3. 濡傛灉鐢ㄦ埛琛ㄨ揪鏈夋槑鏄鹃敊璇紝鍥炲鍚庢寚鍑洪棶棰榎n" +
                "4. 淇濇寔瀵硅瘽鑷劧娴佺晠锛屾ā鎷熺湡瀹炲璇漒n" +
                "5. 姣?-5杞悗锛岃闂敤鎴锋槸鍚﹂渶瑕佹€荤粨\n\n" +
                "鏍煎紡绀轰緥锛歕n" +
                "[AI]: How can I help you today?\n" +
                "[璇勫垎: 9/10] 鍥炲鍦伴亾锛屽彂闊虫竻鏅般€俓n\n" +
                "鐜板湪寮€濮嬪璇濓細";

        if (!history.isEmpty()) {
            systemPrompt = "瀵硅瘽鍘嗗彶锛歕n" + history + "\n\n" + systemPrompt;
        }

        return systemPrompt + "\n\n鐢ㄦ埛锛? + message + "\n[AI]:";
    }
}