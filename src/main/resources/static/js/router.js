const Router = {
    currentPage: null,
    
    routes: {
        'room': {
            template: '/templates/room-template.html',
            init: function() {
                // 房间页面初始化逻辑
            }
        },
        'game': {
            template: '/templates/game-template.html',
            init: function() {
                // 游戏页面初始化逻辑
            }
        }
    },

    init() {
        // 监听浏览器前进后退
        window.addEventListener('popstate', (e) => {
            if (e.state && e.state.page) {
                this.navigateTo(e.state.page, true);
            }
        });
    },

    async navigateTo(page, isPopState = false) {
        const route = this.routes[page];
        if (!route) return;

        // 加载模板
        try {
            const response = await fetch(route.template);
            const html = await response.text();
            
            // 更新页面内容
            document.getElementById(page + 'Page').innerHTML = html;
            
            // 隐藏当前页面，显示新页面
            if (this.currentPage) {
                document.getElementById(this.currentPage + 'Page').classList.remove('active');
            }
            document.getElementById(page + 'Page').classList.add('active');
            
            // 初始化新页面
            route.init();
            
            // 更新当前页面
            this.currentPage = page;
            
            // 更新浏览器历史
            if (!isPopState) {
                history.pushState({ page }, '', `#${page}`);
            }
        } catch (error) {
            console.error('Failed to load template:', error);
        }
    }
}; 