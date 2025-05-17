# Fabric 極限生存簡單版
修改死亡邏輯
1. 死亡時會將玩家傳送回復活點
2. 給予玩家 Debuff
    ```
    StatusEffects.SLOWNESS,
    StatusEffects.REGENERATION,
    StatusEffects.RESISTANCE,
    StatusEffects.BLINDNESS,
    StatusEffects.JUMP_BOOST,
    StatusEffects.MINING_FATIGUE,
    StatusEffects.INVISIBILITY
    ```
3. 死亡倒數後給予幾秒的觀察者模式
4. 觀察者模式結束後切換回生存模式
5. 給予復活無敵時間


## 參考文檔
1. https://fabricmc.net/develop/
2. https://fabricmc.net/

For setup instructions please see the [fabric documentation page](https://docs.fabricmc.net/develop/getting-started/setting-up-a-development-environment) that relates to the IDE that you are using.

## 開發
1. 本地啟動一個測試伺服器
    ``` bash
    ./gradlew runServer
    ```
2. 編譯成jar檔案
    ``` bash
    ./gradlew build
    ```

## TODO

- [ ] 指令 /deathlist 查看其他人死亡倒數
- [ ] Command Code 優化將 watch 邏輯拉到 command 資料夾底下