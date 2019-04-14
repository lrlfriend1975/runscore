var platformVM = new Vue({
	el : '#platform',
	data : {
		name : '',
		addOrUpdatePlatformFlag : false,
		platformActionTitle : '',
		editPlatform : {},
	},
	computed : {},
	created : function() {
	},
	mounted : function() {
		this.initTable();
	},
	methods : {

		initTable : function() {
			var that = this;
			$('.platform-table').bootstrapTable({
				classes : 'table table-hover',
				height : 490,
				url : '/platform/findPlatformByPage',
				pagination : true,
				sidePagination : 'server',
				pageNumber : 1,
				pageSize : 10,
				pageList : [ 10, 25, 50, 100 ],
				queryParamsType : '',
				queryParams : function(params) {
					var condParam = {
						pageSize : params.pageSize,
						pageNum : params.pageNumber,
						name : that.name
					};
					return condParam;
				},
				responseHandler : function(res) {
					return {
						total : res.data.total,
						rows : res.data.content
					};
				},
				columns : [ {
					field : 'name',
					title : '平台名称'
				}, {
					field : 'secretKey',
					title : '接入密钥'
				}, {
					field : 'createTime',
					title : '接入时间'
				}, {
					title : '操作',
					formatter : function(value, row, index) {
						return [ '<button type="button" class="platform-edit-btn btn btn-outline-primary btn-sm" style="margin-right: 4px;">编辑</button>', '<button type="button" class="del-platform-btn btn btn-outline-danger btn-sm">删除</button>' ].join('');
					},
					events : {
						'click .platform-edit-btn' : function(event, value, row, index) {
							that.showPlatformEditModal(row.id);
						},
						'click .del-platform-btn' : function(event, value, row, index) {
							that.delPlatform(row.id);
						}
					}
				} ]
			});
		},
		refreshTable : function() {
			$('.platform-table').bootstrapTable('refreshOptions', {
				pageNumber : 1
			});
		},

		openAddPlatformModal : function() {
			this.addOrUpdatePlatformFlag = true;
			this.platformActionTitle = '新增平台';
			this.editPlatform = {
				name : '',
				secretKey : ''
			}
		},

		showPlatformEditModal : function(id) {
			var that = this;
			that.$http.get('/platform/findPlatformById', {
				params : {
					id : id
				}
			}).then(function(res) {
				that.editPlatform = res.body.data;
				that.addOrUpdatePlatformFlag = true;
				that.configActionTitle = '编辑平台';
			});
		},

		addOrUpdatePlatform : function() {
			var that = this;
			var editPlatform = that.editPlatform;
			if (editPlatform.name == null || editPlatform.name == '') {
				layer.alert('请输入平台名称', {
					title : '提示',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (editPlatform.secretKey == null || editPlatform.secretKey == '') {
				layer.alert('请输入接入密钥', {
					title : '提示',
					icon : 7,
					time : 3000
				});
				return;
			}
			that.$http.post('/platform/addOrUpdatePlatform', editPlatform, {
				emulateJSON : true
			}).then(function(res) {
				layer.alert('操作成功!', {
					icon : 1,
					time : 3000,
					shade : false
				});
				that.addOrUpdatePlatformFlag = false;
				that.refreshTable();
			});
		},

		delPlatform : function(id) {
			var that = this;
			layer.confirm('确定要删除该账号吗?', {
				icon : 7,
				title : '提示'
			}, function(index) {
				layer.close(index);
				that.$http.get('/platform/delPlatformById', {
					params : {
						id : id
					}
				}).then(function(res) {
					layer.alert('操作成功!', {
						icon : 1,
						time : 3000,
						shade : false
					});
					that.refreshTable();
				});
			});
		}

	}
});