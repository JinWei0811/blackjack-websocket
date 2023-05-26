# BlackJack WebSocket API

內容傳遞/接收皆為 .json 檔案

## 連接 WebSocket
在建立 WebSocket 連線之前，請使用以下程式碼進行連線：


``` const socket = webSocket('ws://localhost:8080/connect');  ```

## 訂閱 WebSocket 
設定好 WebSocket 連線內容後，利用以下程式碼進行消息監聽：

```
    this.socket$.subscribe(
      message => { console.log('接收到消息:', message); },
      error   => { console.error('WebSocket錯誤:', error); },
      ()      => { console.log('WebSocket連接關閉'); }
    );
```

## 事件和訊息
在建立連線後，運用以下程式碼進行訊息傳遞

```this.socket$.next({ name: '1', method: 'create' });```

# 訊息格式

## 建立房間
- 輸入
  - name (string)：使用者名稱
  - method (string)：create
- 範例
  ```
    const createRoomMessage = {
      name: 'simon',
      method: 'create'};
      socket.next(createRoomMessage);
  ```
- 輸出
  - roomId(string)：房間 ID
  - playerList(string[])：參與者名單
  - playerStateList(string[])：參與者狀態
  - content(string)：成功訊息
- 範例 
  ```
  const createRoomResponse = {
    roomId: 'g1Y73I',
    playerList: ['simon', 'bot' ],
    playerStateList: ['not ready', 'ready],
    content: '成功建立新房間'};
  ```

## 加入房間
- 輸入
   - name(string)：使用者名稱
   - method(string)：join
   - roomId(string)：房間 ID
- 範例
  ```
    const joinRoomMessage = {
      name: 'aaron',
      method: 'join',
      roomId: 'g1Y73I'};
      socket.next(joinRoomMessage);
  ```
 - 輸出
    - roomId(string)：房間 ID
    - playerList(string[])：參與者名單
    - playerStateList(string[])：參與者狀態
    - content(string)：成功訊息
- 範例
    ```
    const joinRoomResponse = {
      roomId: 'g1Y73I',
      playerList: ['simon', 'bot', 'aaron'],
      playerStateList: ['not ready', 'ready, 'not ready'],
      content: 'aaron 成功加入房間'};
    ```
## 離開房間
- 輸入
   - name(string)：使用者名稱
   - method(string)：leave
   - roomId(string)：房間 ID
- 範例
  ```
    const leaveRoomMessage = {
      name: 'simon',
      method: 'leave',
      roomId: 'g1Y73I'};
      socket.next(leaveRoomMessage);
  ```
 - 輸出
    - roomId(string)：房間 ID
    - playerList(string[])：參與者名單
    - playerStateList(string[])：參與者狀態
    - content(string)：成功訊息
- 範例
    ```
    const leaveRoomResponse = {
      roomId: 'g1Y73I',
      playerList: ['simon', 'bot'],
      playerStateList: ['not ready', 'ready],
      content: 'aaron 離開房間'};
    ```

## 玩家準備就緒
- 輸入
   - name(string)：使用者名稱
   - method(string)：ready
   - roomId(string)：房間 ID
- 範例
  ```
    const playerReadyMessage = {
      name: 'aaron',
      method: 'ready',
      roomId: 'g1Y73I'};
      socket.next(playerReadyMessage);
  ```
- 輸出
    - roomId(string)：房間 ID
    - playerList(string[])：參與者名單
    - playerStateList(string[])：參與者狀態
    - content(string)：成功訊息
- 範例
    ```
    const playerReadyResponse = {
      roomId: 'g1Y73I',
      playerList: ['simon', 'bot', 'aaron'],
      playerStateList: ['not ready', 'ready', 'ready'],
      content: 'aaron 玩家已準備就緒'};
    ```
## 玩家取消準備
- 輸入
   - name(string)：使用者名稱
   - method(string)：not ready
   - roomId(string)：房間 ID
- 範例
  ```
    const playerNotReadyMessage = {
      name: 'aaron',
      method: 'not ready',
      roomId: 'g1Y73I'};
      socket.next(playerNotReadyMessage);
  ```
- 輸出
    - roomId(string)：房間 ID
    - playerList(string[])：參與者名單
    - playerStateList(string[])：參與者狀態
    - content(string)：成功訊息
- 範例
    ```
    const playerNotReadyResponse = {
      roomId: 'g1Y73I',
      playerList: ['simon', 'bot', 'aaron'],
      playerStateList: ['not ready', 'ready', 'not ready'],
      content: 'aaron 玩家取消準備'};
    ```

## 開始遊戲
當所有玩家準備過五秒後會開始遊戲(五秒內有人取消準備，則不開始)。
- 輸入：無
- 輸出
    - roomId(string)：房間 ID
    - playerList(string[])：參與者名單
    - playerStateList(string[])：參與者狀態
    - content(string)：成功訊息
- 範例
    ```
    const gameStartResponse = {
      roomId: 'g1Y73I',
      playerList: ['simon', 'bot'],
      playerStateList: ['continue', 'continue'],
      content: '遊戲開始'};
    ```
## 發牌(總共會有兩次)
遊戲開始後，直接完成兩次發牌(每個人兩張)。

撲克牌花色：Spades(黑陶), Hearts(紅心), Diamonds(方塊), Clubs(梅花)
撲克牌數字：'2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A'
- 輸入：無
- 輸出
  - name(string)：玩家名稱
  - suits(string[])：花色列表
  - ranks(string[])：數字列表
  - state(string)：狀態
  - points(int)：點數總和
- 範例
  ```
  const getCardResponse={
    name: 'simon',
    suits: ['Diamonds'],
    ranks: ['7'],
    state: 'continue',
    points: 7  }
  ```


## 加牌(Hit)
如果加牌後點數超過21點，則輸出 state 為 bust。
- 輸入
   - name(string)：使用者名稱
   - method(string)：hit
   - roomId(string)：房間 ID
- 範例
  ```
    const playerHitMessage = {
      name: 'simon',
      method: 'hit',
      roomId: 'g1Y73I'};
      socket.next(playerHitMessage);
  ```
- 輸出
  - name(string)：玩家名稱
  - suits(string[])：花色列表
  - ranks(string[])：數字列表
  - state(string)：狀態
  - points(int)：點數總和
- 範例
  ```
  const playerHitResponse={
    name: 'simon',
    suits: ['Diamonds', 'Clubs', 'Diamonds'],
    ranks: ['7', '4', '9'],
    state: 'continue',
    points: 20  }
  ```

## 不加牌
  - 輸入
   - name(string)：使用者名稱
   - method(string)：skip
   - roomId(string)：房間 ID
- 範例
  ```
    const playerSkipMessage = {
      name: 'simon',
      method: 'skip',
      roomId: 'g1Y73I'};
      socket.next(playerSkipMessage);
  ```
- 輸出
  - name(string)：玩家名稱
  - suits(string[])：花色列表
  - ranks(string[])：數字列表
  - state(string)：狀態
  - points(int)：點數總和
- 範例
  ```
  const playerSkipResponse={
    name: 'simon',
    suits: ['Diamonds', 'Clubs', 'Diamonds'],
    ranks: ['7', '4', '9'],
    state: 'skip',
    points: 20  }
  ```

## 電腦加牌
  當所有玩家的狀態都為 skip 時，則電腦開始計算是否加牌。

  BOT可能狀態： 繼續加牌/continue 停止加牌/skip 爆牌/bust
- 輸入 無
- 輸出
  - name(string)：玩家名稱
  - suits(string[])：花色列表
  - ranks(string[])：數字列表
  - state(string)：狀態
  - points(int)：點數總和
- 範例
  ```
  const playerSkipResponse={
    name: 'bot',
    suits: ['Spades', 'Hearts', 'Spades'],
    ranks: ['8', '4', '9'],
    state: 'skip',
    points: 21  }
  ```

## 輸贏結果
當電腦加牌結束，則輸出結果 

result： 贏/win 輸/lose
- 輸入 無
- 輸出
  - roomId(string)：房間 ID
  - name(string[])：玩家名稱
  - result(string[])：結果
- 範例
  ```
  const gameResultResponse={
    roomId: 'g1Y73I',
    name: 'simon',
    result: 'lose' }
  ```

## 回傳最終結果

- 輸入 無
- 輸出
  - names(string[])：參與者列表
  - chips(int[])：代幣列表
- 範例
  ```
  const countResultResponse={
    name: ['simon','bot'],
    chips: [900,1100 ] }
  ```