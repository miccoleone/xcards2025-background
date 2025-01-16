const WebSocketManager = {
    gameSocket: null,
    roomSocket: null,
    deviceId: null,

    init() {
        this.deviceId = localStorage.getItem("deviceId") || generateDeviceId();
        this.connectWebSockets();
    },

    connectWebSockets() {
        // 连接游戏WebSocket
        this.gameSocket = new WebSocket(`ws://118.31.102.119:4396/game?deviceId=${this.deviceId}`);
        this.gameSocket.onopen = () => console.log('Game WebSocket connected');
        this.gameSocket.onclose = () => console.log('Game WebSocket closed');
        this.gameSocket.onerror = (error) => console.error('Game WebSocket error:', error);
        
        // 连接房间WebSocket
        this.roomSocket = new WebSocket(`ws://118.31.102.119:4396/room?deviceId=${this.deviceId}`);
        this.roomSocket.onopen = () => console.log('Room WebSocket connected');
        this.roomSocket.onclose = () => console.log('Room WebSocket closed');
        this.roomSocket.onerror = (error) => console.error('Room WebSocket error:', error);
        
        // 设置消息处理器
        this.setupMessageHandlers();
    },

    setupMessageHandlers() {
        this.gameSocket.onmessage = this.handleGameMessage.bind(this);
        this.roomSocket.onmessage = this.handleRoomMessage.bind(this);
    },

    handleGameMessage(event) {
        // 处理游戏消息
        const message = event.data;
        // 触发自定义事件
        window.dispatchEvent(new CustomEvent('gameMessage', { detail: message }));
    },

    handleRoomMessage(event) {
        // 处理房间消息
        const message = JSON.parse(event.data);
        // 触发自定义事件
        window.dispatchEvent(new CustomEvent('roomMessage', { detail: message }));
    },

    sendGameMessage(message) {
        if (this.gameSocket && this.gameSocket.readyState === WebSocket.OPEN) {
            this.gameSocket.send(JSON.stringify(message));
        }
    },

    sendRoomMessage(message) {
        if (this.roomSocket && this.roomSocket.readyState === WebSocket.OPEN) {
            this.roomSocket.send(JSON.stringify(message));
        }
    }
}; 