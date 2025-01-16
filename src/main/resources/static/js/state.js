const GameState = {
    currentPlayer: null,
    roomCode: null,
    gameStatus: null,
    
    init() {
        this.reset();
    },
    
    reset() {
        this.currentPlayer = {
            deviceId: localStorage.getItem("deviceId") || generateDeviceId(),
            nickname: localStorage.getItem("nickname") || "匿名玩家",
            role: null,
            winRate: null
        };
        this.roomCode = null;
        this.gameStatus = 'waiting'; // waiting, playing, finished
    },
    
    updatePlayer(data) {
        Object.assign(this.currentPlayer, data);
    },
    
    setRoomCode(code) {
        this.roomCode = code;
    },
    
    setGameStatus(status) {
        this.gameStatus = status;
    }
}; 