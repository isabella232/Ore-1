import {API} from "../api";

const state = {
    currentUser: null
}
const mutations =  {
    setUser(state, payload) {
        state.currentUser = payload.user;
    }
}
const actions = {
    loadUser(context) {
        if(API.hasUser()) {
            API.request("users/@me").then((res) => {
                context.commit({
                    type: 'setUser',
                    user: res
                });
            })
        }
    }
}

export default {
    namespaced: true,
    state,
    mutations,
    actions
}