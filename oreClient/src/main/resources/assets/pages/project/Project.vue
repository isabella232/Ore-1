<template>
    <div v-if="currentProject">
        <project-header :project="currentProject" :permissions="permissions" :members="members"
                        :current-user="currentUser"
                        v-on:set-watching="updateWatching" v-on:toggle-starred="toggleStarred"/>

        <router-view :project="currentProject" :permissions="permissions" :members="members"/>
    </div>
</template>

<script>
    import ProjectHeader from "./../../components/ProjectHeader";
    import {API} from "../../api";

    export default {
        components: {
            ProjectHeader,
        },
        data() {
            return {
                fetchedProject: null,
                permissions: [],
                members: [],
                currentUser: null //TODO
            }
        },
        props: {
            pluginId: String,
            owner: String,
            slug: String,
            project: {
                type: Object,
                required: false,
                default: null
            }
        },
        computed: {
            currentProject() {
                return this.fetchedProject || this.project;
            }
        },
        created() {
            if (!this.pluginId && (!this.owner || !this.slug)) {
                throw "Neither pluginId or namespace for project defined"
            }

            if (!this.project) {
                let exactQuery = () => API.request('projects?exact=true&owner=' + this.owner + '&q=' + this.slug).then(res => {
                    if(res.result.length) {
                        return res.result[0];
                    }
                    else {
                        throw "Project not found"
                    }
                })
                let futureProject = this.pluginId ? API.request("projects/" + this.pluginId) : exactQuery();

                futureProject.then((response) => {
                    this.fetchedProject = response;

                    if (!this.pluginId) {
                        API.request("permissions", "GET", {'pluginId': this.fetchedProject.plugin_id}).then((response) => {
                            this.permissions = response.permissions;
                        });

                        API.request("projects/" + this.fetchedProject.plugin_id + "/members").then((response) => {
                            this.members = response
                        });
                    }
                });
            }

            if (this.pluginId) {
                API.request("permissions", "GET", {'pluginId': this.pluginId}).then((response) => {
                    this.permissions = response.permissions;
                });

                API.request("projects/" + this.pluginId + "/members").then((response) => {
                    this.members = response
                });
            }

            if (API.hasUser()) {
                API.request("users/@me").then((res) => {
                    this.currentUser = res;
                })
            }
        },
        methods: {
            toggleStarred() {
                if (this.fetchedProject) {
                    let newStarred = !this.fetchedProject.user_actions.starred;
                    if (newStarred) {
                        this.fetchedProject.stats.stars += 1;
                    } else {
                        this.fetchedProject.stats.stars -= 1;
                    }

                    this.fetchedProject.user_actions.starred = newStarred;
                } else {
                    this.$emit('toggle-starred')
                }
            },
            updateWatching(newWatching) {
                if (this.fetchedProject) {
                    if (newWatching) {
                        this.fetchedProject.stats.watchers += 1;
                    } else {
                        this.fetchedProject.stats.watchers -= 1;
                    }

                    this.fetchedProject.user_actions.watching = newWatching;
                } else {
                    this.$emit('update-watching', newWatching)
                }
            }
        }
    }
</script>