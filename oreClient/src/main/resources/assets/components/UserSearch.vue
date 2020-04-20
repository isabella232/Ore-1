<template>
    <div>
        <div class="input-group input-group-sm">
            <input type="text" class="form-control" placeholder="Add User…" v-model="query"/>
        </div>
        <div v-if="users.length" class="open">
            <ul class="dropdown-menu">
                <li v-for="user in users">
                    <a href="#" @click="() => {$emit('add-user', user); reset()}">
                        <icon :src="avatarUrl(user.name)" extra-classes="user-avatar-xs"></icon>
                        {{ user.name }}
                    </a>
                </li>
            </ul>
        </div>
    </div>
</template>

<script>
    import Icon from "./Icon";
    import debounce from "lodash/debounce";
    import {API} from "../api";
    import {avatarUrl} from "../utils";

    export default {
        components: {
            Icon
        },
        data() {
            return {
                query: '',
                users: []
            }
        },
        props: {
            exclude: {
                type: Array,
                default: []
            }
        },
        watch: {
            query() {
                this.updateUser()
            }
        },
        methods: {
            updateUser: debounce(function () {
                if (this.query === '') {
                    this.users = []
                } else {
                    API.request('users?q=' + this.query + '&limit=' + 5).then(res => {
                        this.users = res.result.filter(u => !this.exclude.includes(u.name));
                    })
                }
            }, 250),
            avatarUrl(name) {
                return avatarUrl(name);
            },
            reset() {
                this.query = '';
                this.users = [];
            }
        }
    }

</script>